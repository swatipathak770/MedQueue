package com.medqueue;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.entity.*;
import com.medqueue.exception.ConflictException;
import com.medqueue.repository.*;
import com.medqueue.service.AppointmentService;
import com.medqueue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueConcurrencyIntegrationTest {
    @Autowired QueueService queues;
    @Autowired AppointmentService booking;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentStatusHistoryRepository statusHistory;
    @Autowired QueueStateRepository states;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void twoConcurrentCallNextRequestsCannotCallTheSameQueueTwice() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        String email = "queue-race-" + UUID.randomUUID() + "@example.test";
        Fixture fixture = tx.execute(status -> {
            User doctorUser = users.saveAndFlush(new User("Queue Race Doctor", email, "test-hash", Role.DOCTOR, null));
            User patient = users.saveAndFlush(new User("Queue Race Patient", "patient-" + UUID.randomUUID() + "@example.test", "test-hash", Role.PATIENT, null));
            Department department = departments.saveAndFlush(new Department("Race " + UUID.randomUUID()));
            Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));
            Appointment appointment = appointments.saveAndFlush(new Appointment(patient, doctor, LocalDate.now(), null, 1));
            return new Fixture(doctor.getId(), doctorUser.getId(), patient.getId(), department.getId(), appointment.getId(), email, null);
        });

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> call = () -> {
                start.await();
                try { queues.callNext(email); return true; }
                catch (ConflictException expected) { return false; }
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            boolean firstCalled = first.get(20, TimeUnit.SECONDS);
            boolean secondCalled = second.get(20, TimeUnit.SECONDS);
            assertThat((firstCalled ? 1 : 0) + (secondCalled ? 1 : 0)).isEqualTo(1);
            assertThat(appointments.findById(fixture.appointmentId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CALLED);
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status -> {
                statusHistory.deleteByAppointmentId(fixture.appointmentId());
                appointments.deleteById(fixture.appointmentId());
                states.findByDoctorIdAndQueueDate(fixture.doctorId(), LocalDate.now()).ifPresent(states::delete);
                doctors.deleteById(fixture.doctorId());
                users.deleteById(fixture.patientUserId()); users.deleteById(fixture.doctorUserId());
                departments.deleteById(fixture.departmentId());
            });
        }
    }

    @Test void repeatedCallNextIsRejectedWithoutCallingAnotherAppointmentOrDuplicatingHistory() throws Exception {
        Fixture fixture = createFlowFixture();
        try {
            Appointment first = book(fixture);
            Appointment second = book(fixture);

            assertThat(queues.callNext(fixture.email()).getId()).isEqualTo(first.getId());
            assertThatThrownBy(() -> queues.callNext(fixture.email())).isInstanceOf(ConflictException.class);

            assertThat(appointments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CALLED);
            assertThat(appointments.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.WAITING);
            assertThat(transitions(fixture)).filteredOn(h -> h.getNewStatus() == AppointmentStatus.CALLED).hasSize(1);
        } finally {
            cleanup(fixture);
        }
    }

    @Test void repeatedCompleteIsRejectedWithoutDuplicatingStatusHistory() throws Exception {
        Fixture fixture = createFlowFixture();
        try {
            Appointment appointment = book(fixture);
            queues.callNext(fixture.email());

            queues.complete(fixture.email(), appointment.getId());
            assertThatThrownBy(() -> queues.complete(fixture.email(), appointment.getId())).isInstanceOf(ConflictException.class);

            assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.DONE);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId()))
                    .filteredOn(h -> h.getOldStatus() == AppointmentStatus.CALLED && h.getNewStatus() == AppointmentStatus.DONE)
                    .hasSize(1);
        } finally {
            cleanup(fixture);
        }
    }

    @Test void repeatedSkipIsRejectedWithoutDuplicatingStatusHistory() throws Exception {
        Fixture fixture = createFlowFixture();
        try {
            Appointment appointment = book(fixture);
            queues.callNext(fixture.email());

            queues.skip(fixture.email(), appointment.getId());
            assertThatThrownBy(() -> queues.skip(fixture.email(), appointment.getId())).isInstanceOf(ConflictException.class);

            assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.SKIPPED);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId()))
                    .filteredOn(h -> h.getOldStatus() == AppointmentStatus.CALLED && h.getNewStatus() == AppointmentStatus.SKIPPED)
                    .hasSize(1);
        } finally {
            cleanup(fixture);
        }
    }

    @Test void twoConcurrentCompleteRequestsCreateOnlyOneDoneTransition() throws Exception {
        Fixture fixture = createFlowFixture();
        try {
            Appointment appointment = book(fixture);
            queues.callNext(fixture.email());

            List<Outcome> outcomes = concurrently(() -> queues.complete(fixture.email(), appointment.getId()));

            assertThat(outcomes).containsExactlyInAnyOrder(Outcome.SUCCEEDED, Outcome.REJECTED);
            assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.DONE);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId()))
                    .filteredOn(h -> h.getOldStatus() == AppointmentStatus.CALLED && h.getNewStatus() == AppointmentStatus.DONE)
                    .hasSize(1);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId())).hasSize(3);
        } finally {
            cleanup(fixture);
        }
    }

    @Test void twoConcurrentSkipRequestsCreateOnlyOneSkippedTransition() throws Exception {
        Fixture fixture = createFlowFixture();
        try {
            Appointment appointment = book(fixture);
            queues.callNext(fixture.email());

            List<Outcome> outcomes = concurrently(() -> queues.skip(fixture.email(), appointment.getId()));

            assertThat(outcomes).containsExactlyInAnyOrder(Outcome.SUCCEEDED, Outcome.REJECTED);
            assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.SKIPPED);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId()))
                    .filteredOn(h -> h.getOldStatus() == AppointmentStatus.CALLED && h.getNewStatus() == AppointmentStatus.SKIPPED)
                    .hasSize(1);
            assertThat(statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId())).hasSize(3);
        } finally {
            cleanup(fixture);
        }
    }

    private Fixture createFlowFixture() {
        var tx = new TransactionTemplate(transactionManager);
        String suffix = UUID.randomUUID().toString();
        return tx.execute(status -> {
            User doctorUser = users.saveAndFlush(new User("Queue Flow Doctor", "flow-doctor-" + suffix + "@example.test", "test-hash", Role.DOCTOR, null));
            User patient = users.saveAndFlush(new User("Queue Flow Patient", "flow-patient-" + suffix + "@example.test", "test-hash", Role.PATIENT, null));
            Department department = departments.saveAndFlush(new Department("Flow " + suffix));
            Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));
            return new Fixture(doctor.getId(), doctorUser.getId(), patient.getId(), department.getId(), null,
                    doctorUser.getEmail(), patient.getEmail());
        });
    }

    private Appointment book(Fixture fixture) {
        return booking.book(fixture.patientEmail(), new BookingRequest(fixture.doctorId(), LocalDate.now(), null, true));
    }

    private List<AppointmentStatusHistory> transitions(Fixture fixture) {
        return appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumber(fixture.doctorId(), LocalDate.now()).stream()
                .flatMap(appointment -> statusHistory.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId()).stream())
                .toList();
    }

    private List<Outcome> concurrently(QueueAction action) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Outcome> operation = () -> {
                ready.countDown();
                if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent workers did not become ready");
                start.await();
                try { action.run(); return Outcome.SUCCEEDED; }
                catch (ConflictException expected) { return Outcome.REJECTED; }
            };
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent workers did not become ready");
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private void cleanup(Fixture fixture) {
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            var appointmentsForDoctor = appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumber(fixture.doctorId(), LocalDate.now());
            appointmentsForDoctor.forEach(appointment -> statusHistory.deleteByAppointmentId(appointment.getId()));
            appointments.deleteAll(appointmentsForDoctor);
            states.findByDoctorIdAndQueueDate(fixture.doctorId(), LocalDate.now()).ifPresent(states::delete);
            doctors.deleteById(fixture.doctorId());
            users.deleteById(fixture.patientUserId());
            users.deleteById(fixture.doctorUserId());
            departments.deleteById(fixture.departmentId());
        });
    }

    @FunctionalInterface private interface QueueAction { void run() throws Exception; }
    private enum Outcome { SUCCEEDED, REJECTED }
    private record Fixture(Long doctorId, Long doctorUserId, Long patientUserId, Long departmentId, Long appointmentId,
                           String email, String patientEmail) { }
}
