package com.medqueue;

import com.medqueue.entity.*;
import com.medqueue.repository.*;
import com.medqueue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueConcurrencyIntegrationTest {
    @Autowired QueueService queues;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
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
            return new Fixture(doctor.getId(), doctorUser.getId(), patient.getId(), department.getId(), appointment.getId());
        });

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var call = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { queues.callNext(email); return true; }
                catch (com.medqueue.exception.ConflictException expected) { return false; }
            };
            var first = pool.submit(call); var second = pool.submit(call); start.countDown();
            boolean firstCalled = first.get(); boolean secondCalled = second.get();
            assertThat((firstCalled ? 1 : 0) + (secondCalled ? 1 : 0)).isEqualTo(1);
            assertThat(appointments.findById(fixture.appointmentId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CALLED);
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status -> {
                appointments.deleteById(fixture.appointmentId());
                states.findByDoctorIdAndQueueDate(fixture.doctorId(), LocalDate.now()).ifPresent(states::delete);
                doctors.deleteById(fixture.doctorId());
                users.deleteById(fixture.patientUserId()); users.deleteById(fixture.doctorUserId());
                departments.deleteById(fixture.departmentId());
            });
        }
    }
    private record Fixture(Long doctorId, Long doctorUserId, Long patientUserId, Long departmentId, Long appointmentId) { }
}
