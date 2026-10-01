package com.medqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medqueue.entity.*;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.DepartmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQueueOverviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;

    @Test void adminGetsEveryDoctorQueueSummaryWithoutPatientDetails() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User doctorUser = users.saveAndFlush(new User("Overview Doctor " + suffix.substring(0, 6),
                "overview-doctor-" + suffix + "@example.test", "unused", Role.DOCTOR, null));
        User patient = users.saveAndFlush(new User("Private Patient Name", "overview-patient-" + suffix + "@example.test",
                "unused", Role.PATIENT, null));
        Department department = departments.saveAndFlush(new Department("Overview " + suffix));
        Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));
        appointments.saveAndFlush(new Appointment(patient, doctor, LocalDate.now(), null, 1));
        Appointment called = new Appointment(patient, doctor, LocalDate.now(), null, 2);
        called.transitionTo(com.medqueue.entity.AppointmentStatus.CALLED);
        appointments.saveAndFlush(called);

        mvc.perform(get("/api/admin/queues"))
                .andExpect(status().isUnauthorized());
        String response = mvc.perform(get("/api/admin/queues").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode rows = json.readTree(response);
        JsonNode summary = null;
        for (JsonNode row : rows) {
            if (row.path("doctorId").asLong() == doctor.getId()) summary = row;
        }
        assertThat(summary).isNotNull();
        assertThat(summary.path("doctorName").asText()).startsWith("Overview Doctor");
        assertThat(summary.path("department").asText()).isEqualTo(department.getName());
        assertThat(summary.path("waitingCount").asLong()).isEqualTo(1);
        assertThat(summary.path("activeTokenNumber").asInt()).isEqualTo(2);
        assertThat(summary.path("activeStatus").asText()).isEqualTo("CALLED");
        assertThat(summary.has("patientName")).isFalse();
        assertThat(summary.has("appointmentId")).isFalse();
    }
}
