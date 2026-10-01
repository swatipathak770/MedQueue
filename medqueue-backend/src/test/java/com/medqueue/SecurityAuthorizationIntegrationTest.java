package com.medqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medqueue.entity.*;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.DepartmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.UserRepository;
import com.medqueue.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired AppointmentRepository appointments;
    @Autowired JwtService jwt;
    @Value("${medqueue.jwt.secret}") String configuredSecret;

    private User patientA;
    private User patientB;
    private User doctorUserA;
    private User doctorUserB;
    private User admin;
    private Doctor doctorA;
    private Doctor doctorB;
    private Department department;
    private Appointment appointmentA;
    private Appointment appointmentBWaiting;
    private Appointment appointmentBCalled;
    private String patientToken;
    private String patientBToken;
    private String doctorToken;
    private String doctorBToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        patientA = saveUser("Patient A", "patient-a-" + suffix + "@example.test", Role.PATIENT);
        patientB = saveUser("Patient B", "patient-b-" + suffix + "@example.test", Role.PATIENT);
        doctorUserA = saveUser("Doctor A", "doctor-a-" + suffix + "@example.test", Role.DOCTOR);
        doctorUserB = saveUser("Doctor B", "doctor-b-" + suffix + "@example.test", Role.DOCTOR);
        admin = saveUser("Admin", "admin-" + suffix + "@example.test", Role.ADMIN);
        department = departments.saveAndFlush(new Department("Security " + suffix));
        doctorA = doctors.saveAndFlush(new Doctor(doctorUserA, department, "General", 10));
        doctorB = doctors.saveAndFlush(new Doctor(doctorUserB, department, "General", 10));

        appointmentA = appointments.saveAndFlush(new Appointment(patientA, doctorA, LocalDate.now(), null, 1));
        appointmentBWaiting = appointments.saveAndFlush(new Appointment(patientB, doctorB, LocalDate.now(), null, 1));
        appointmentBCalled = new Appointment(patientB, doctorB, LocalDate.now(), null, 2);
        appointmentBCalled.transitionTo(AppointmentStatus.CALLED);
        appointmentBCalled = appointments.saveAndFlush(appointmentBCalled);

        patientToken = token(patientA);
        patientBToken = token(patientB);
        doctorToken = token(doctorUserA);
        doctorBToken = token(doctorUserB);
        adminToken = token(admin);
    }

    @Test
    void jwtFilterAcceptsValidTokenAndRejectsMissingMalformedExpiredAndInvalidSignatureTokens() throws Exception {
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer(expiredToken(patientA.getEmail()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer(wrongSignatureToken(patientA.getEmail()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtSubjectAndPatientHistoryRemainBoundToTheAuthenticatedUser() throws Exception {
        String response = mvc.perform(get("/api/appointments/me").header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode history = json.readTree(response);
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).path("id").asLong()).isEqualTo(appointmentA.getId());
        assertThat(history.findValuesAsText("id")).doesNotContain(appointmentBWaiting.getId().toString());

        // The API intentionally offers authenticated-user history only; it has no appointment-by-id read or cancel route.
        mvc.perform(get("/api/appointments/{id}", appointmentBWaiting.getId())
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/appointments/{id}", appointmentBWaiting.getId())
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/appointments/{id}/status-history", appointmentA.getId())
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/appointments/{id}/status-history", appointmentBWaiting.getId())
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/{id}/status-history", appointmentBWaiting.getId())
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/{id}/status-history", appointmentA.getId())
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/appointments/{id}/status-history", appointmentBWaiting.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/appointments/{id}/status-history", appointmentA.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleMatrixAndDoctorOwnershipAreEnforcedForRealJwtTokens() throws Exception {
        mvc.perform(get("/api/admin/doctors").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/doctor/queue").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/doctors").header("Authorization", bearer(doctorToken)))
                .andExpect(status().isForbidden());

        String ownQueue = mvc.perform(get("/api/doctor/queue").header("Authorization", bearer(doctorToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode queue = json.readTree(ownQueue);
        assertThat(queue.path("appointments")).hasSize(1);
        assertThat(queue.path("appointments").get(0).path("id").asLong()).isEqualTo(appointmentA.getId());

        String called = mvc.perform(post("/api/doctor/queue/next").header("Authorization", bearer(doctorToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(called).path("id").asLong()).isEqualTo(appointmentA.getId());
        assertThat(appointments.findById(appointmentBWaiting.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.WAITING);

        mvc.perform(post("/api/doctor/queue/{id}/complete", appointmentBCalled.getId())
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/doctor/queue/{id}/skip", appointmentBWaiting.getId())
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/doctor/queue").header("Authorization", bearer(doctorBToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointments", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void adminJwtCanUseAdminCrudSlotsAnalyticsAndLiveOverviewWhileOtherRolesCannot() throws Exception {
        String auth = bearer(adminToken);
        mvc.perform(get("/api/admin/doctors").header("Authorization", auth)).andExpect(status().isOk());

        String createdDoctor = mvc.perform(post("/api/admin/doctors").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Created Doctor", "email", "created-" + UUID.randomUUID() + "@example.test",
                                "password", "TempPassword123", "departmentId", department.getId(), "specialization", "General", "avgConsultMinutes", 12))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long newDoctorId = json.readTree(createdDoctor).path("id").asLong();
        mvc.perform(put("/api/admin/doctors/{id}", newDoctorId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("departmentId", department.getId(), "specialization", "Family Medicine", "avgConsultMinutes", 14))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/doctors/{id}", newDoctorId).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/admin/departments").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "New Department " + UUID.randomUUID()))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/slots").header("Authorization", auth)).andExpect(status().isOk());
        String slotResponse = mvc.perform(post("/api/admin/slots").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("doctorId", doctorA.getId(), "dayOfWeek", "MONDAY",
                                "startTime", LocalTime.of(18, 0), "endTime", LocalTime.of(18, 30), "slotCapacity", 4))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long slotId = json.readTree(slotResponse).path("id").asLong();
        mvc.perform(get("/api/admin/analytics").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/queues").header("Authorization", auth)).andExpect(status().isOk());

        for (String nonAdminToken : new String[]{patientToken, doctorToken}) {
            String nonAdmin = bearer(nonAdminToken);
            mvc.perform(get("/api/admin/doctors").header("Authorization", nonAdmin)).andExpect(status().isForbidden());
            String doctorRequest = json.writeValueAsString(Map.of("name", "Denied Doctor", "email", "denied-" + UUID.randomUUID() + "@example.test",
                    "password", "TempPassword123", "departmentId", department.getId(), "specialization", "General", "avgConsultMinutes", 12));
            mvc.perform(post("/api/admin/doctors").header("Authorization", nonAdmin).contentType(MediaType.APPLICATION_JSON).content(doctorRequest))
                    .andExpect(status().isForbidden());
            String doctorUpdate = json.writeValueAsString(Map.of("departmentId", department.getId(), "specialization", "Denied", "avgConsultMinutes", 12));
            mvc.perform(put("/api/admin/doctors/{id}", doctorB.getId()).header("Authorization", nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content(doctorUpdate))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/doctors/{id}", doctorB.getId()).header("Authorization", nonAdmin))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/departments").header("Authorization", nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied Department\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/departments/{id}", department.getId()).header("Authorization", nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied Rename\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/departments/{id}", department.getId()).header("Authorization", nonAdmin))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/slots").header("Authorization", nonAdmin)).andExpect(status().isForbidden());
            String slotRequest = json.writeValueAsString(Map.of("doctorId", doctorA.getId(), "dayOfWeek", "MONDAY",
                    "startTime", LocalTime.of(18, 0), "endTime", LocalTime.of(18, 30), "slotCapacity", 4));
            mvc.perform(post("/api/admin/slots").header("Authorization", nonAdmin).contentType(MediaType.APPLICATION_JSON).content(slotRequest))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/slots/{id}", slotId).header("Authorization", nonAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content(slotRequest))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/slots/{id}", slotId).header("Authorization", nonAdmin)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/analytics").header("Authorization", nonAdmin)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/queues").header("Authorization", nonAdmin)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/queues")).andExpect(status().isUnauthorized());
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "test-password-hash", role, null));
    }

    private String token(User user) {
        UserDetails principal = org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password("unused").roles(user.getRole().name()).build();
        return jwt.generateToken(principal);
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String expiredToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60))).signWith(configuredKey()).compact();
    }

    private String wrongSignatureToken(String subject) {
        SecretKey otherKey = Keys.hmacShaKeyFor("different-signature-key-with-32-bytes".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600))).signWith(otherKey).compact();
    }

    private SecretKey configuredKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(configuredSecret));
    }
}
