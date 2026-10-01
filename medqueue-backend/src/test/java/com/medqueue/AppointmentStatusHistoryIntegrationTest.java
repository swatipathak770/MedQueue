package com.medqueue;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.entity.*;
import com.medqueue.repository.*;
import com.medqueue.service.AppointmentService;
import com.medqueue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AppointmentStatusHistoryIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentService appointments;
    @Autowired QueueService queues;
    @Autowired AppointmentStatusHistoryRepository history;

    @Test
    void recordsInitialAndQueueTransitionsChronologicallyWithActorIds() {
        String suffix = UUID.randomUUID().toString();
        User patient = users.saveAndFlush(new User("Audit Patient", "audit-patient-" + suffix + "@example.test", "unused", Role.PATIENT, null));
        User doctorUser = users.saveAndFlush(new User("Audit Doctor", "audit-doctor-" + suffix + "@example.test", "unused", Role.DOCTOR, null));
        Department department = departments.saveAndFlush(new Department("Audit " + suffix));
        Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));

        Appointment appointment = appointments.book(patient.getEmail(), new BookingRequest(doctor.getId(), LocalDate.now(), null, true));
        queues.callNext(doctorUser.getEmail());
        queues.complete(doctorUser.getEmail(), appointment.getId());

        var entries = history.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId());
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(AppointmentStatusHistory::getOldStatus)
                .containsExactly(null, AppointmentStatus.WAITING, AppointmentStatus.CALLED);
        assertThat(entries).extracting(AppointmentStatusHistory::getNewStatus)
                .containsExactly(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.DONE);
        assertThat(entries).extracting(AppointmentStatusHistory::getChangedBy)
                .containsExactly("USER:" + patient.getId(), "USER:" + doctorUser.getId(), "USER:" + doctorUser.getId());
        assertThat(entries.get(0).getChangedAt()).isBeforeOrEqualTo(entries.get(1).getChangedAt());
        assertThat(entries.get(1).getChangedAt()).isBeforeOrEqualTo(entries.get(2).getChangedAt());
    }
}
