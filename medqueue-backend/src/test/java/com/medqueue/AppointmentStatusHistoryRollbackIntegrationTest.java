package com.medqueue;

import com.medqueue.entity.*;
import com.medqueue.repository.*;
import com.medqueue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
class AppointmentStatusHistoryRollbackIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
    @Autowired QueueStateRepository states;
    @Autowired QueueService queues;
    @MockitoSpyBean AppointmentStatusHistoryRepository history;

    @Test
    void statusAndQueueStateRollBackWhenAuditInsertFails() {
        String suffix = UUID.randomUUID().toString();
        User patient = users.saveAndFlush(new User("Rollback Patient", "rollback-patient-" + suffix + "@example.test", "unused", Role.PATIENT, null));
        User doctorUser = users.saveAndFlush(new User("Rollback Doctor", "rollback-doctor-" + suffix + "@example.test", "unused", Role.DOCTOR, null));
        Department department = departments.saveAndFlush(new Department("Rollback " + suffix));
        Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));
        Appointment appointment = appointments.saveAndFlush(new Appointment(patient, doctor, LocalDate.now(), null, 1));
        doThrow(new DataIntegrityViolationException("simulated audit insert failure"))
                .when(history).save(any(AppointmentStatusHistory.class));

        assertThatThrownBy(() -> queues.callNext(doctorUser.getEmail()))
                .isInstanceOf(DataIntegrityViolationException.class);

        reset(history);
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.WAITING);
        assertThat(states.findByDoctorIdAndQueueDate(doctor.getId(), LocalDate.now())).isEmpty();
        assertThat(history.findByAppointmentIdOrderByChangedAtAscIdAsc(appointment.getId())).isEmpty();
    }
}
