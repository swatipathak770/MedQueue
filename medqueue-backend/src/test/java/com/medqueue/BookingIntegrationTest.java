package com.medqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medqueue.entity.*;
import com.medqueue.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class BookingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired SlotRepository slots;

    @Test void patientCanSeeCapacityBookAndReceiveTheQueueToken() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        User doctorUser = users.saveAndFlush(new User("Booking Doctor", "booking-" + UUID.randomUUID() + "@example.test", "unused", Role.DOCTOR, null));
        Department department = departments.saveAndFlush(new Department("Booking " + UUID.randomUUID()));
        Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 12));
        Slot slot = slots.saveAndFlush(new Slot(doctor, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), 1));
        String email = "booking-patient-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("name", "Booking Patient", "email", email, "password", "strong-password"))))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "strong-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + login.get("token").asText();

        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].remainingCapacity").value(1)).andExpect(jsonPath("$[0].available").value(true));
        String booking = json.writeValueAsString(java.util.Map.of("doctorId", doctor.getId(), "appointmentDate", date, "slotId", slot.getId(), "walkIn", false));
        mvc.perform(post("/api/appointments").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tokenNumber").value(1)).andExpect(jsonPath("$.queuePosition").value(1));
        mvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].remainingCapacity").value(0)).andExpect(jsonPath("$[0].available").value(false));
        mvc.perform(post("/api/appointments").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].doctorName").value("Booking Doctor"));
    }
}
