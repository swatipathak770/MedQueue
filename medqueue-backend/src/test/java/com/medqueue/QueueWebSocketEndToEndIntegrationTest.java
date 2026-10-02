package com.medqueue;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.dto.response.AppointmentResponse;
import com.medqueue.dto.response.QueueUpdateResponse;
import com.medqueue.entity.*;
import com.medqueue.repository.DepartmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.UserRepository;
import com.medqueue.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueWebSocketEndToEndIntegrationTest {
    private static final long WAIT_SECONDS = 8;
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired DoctorRepository doctors;
    @Autowired JwtService jwt;
    @Autowired SimpUserRegistry userRegistry;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired ObjectMapper objectMapper;

    private final List<StompSession> sessions = new CopyOnWriteArrayList<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private WebSocketStompClient stompClient;

    @Test
    void authenticatedDoctorReceivesSnapshotsForBookingCallCompleteAndSkipAndCannotSubscribeCrossDoctor() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User patient = saveUser("WS Patient", "ws-patient-" + suffix + "@example.test", Role.PATIENT);
        User doctorUser = saveUser("WS Doctor", "ws-doctor-" + suffix + "@example.test", Role.DOCTOR);
        User otherDoctorUser = saveUser("Other WS Doctor", "ws-other-doctor-" + suffix + "@example.test", Role.DOCTOR);
        Department department = departments.saveAndFlush(new Department("WS " + suffix));
        Doctor doctor = doctors.saveAndFlush(new Doctor(doctorUser, department, "General", 10));
        Doctor otherDoctor = doctors.saveAndFlush(new Doctor(otherDoctorUser, department, "General", 10));

        startClient();
        var validHandler = new SessionHandler();
        StompSession doctorSession = connect(token(doctorUser), validHandler);
        assertThat(doctorSession.isConnected()).isTrue();
        doctorSession.subscribe("/topic/queue/" + doctor.getId(), new SnapshotHandler(validHandler.messages));
        String topic = "/topic/queue/" + doctor.getId();
        awaitSubscription(doctorUser.getEmail(), topic);
        messaging.convertAndSend(topic, new QueueUpdateResponse(doctor.getId(), LocalDate.now(), Instant.now(), true, true, 0, 0, List.of()));
        QueueUpdateResponse probe = validHandler.messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(probe).withFailMessage("STOMP probe delivery failed; handler errors: %s", validHandler.errorFrames).isNotNull();
        assertThat(probe.doctorId()).isEqualTo(doctor.getId());

        SessionHandler deniedHandler = new SessionHandler();
        StompSession deniedSession = connect(token(doctorUser), deniedHandler);
        deniedSession.subscribe("/topic/queue/" + otherDoctor.getId(), new SnapshotHandler(deniedHandler.messages));
        assertThat(deniedHandler.errorFrames.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        AppointmentResponse booked = http.exchange(url("/api/appointments"), HttpMethod.POST,
                authorized(token(patient), new BookingRequest(doctor.getId(), LocalDate.now(), null, true)), AppointmentResponse.class)
                .getBody();
        assertNotNull(booked);
        QueueUpdateResponse bookingUpdate = await(validHandler.messages,
                snapshot -> snapshot.waitingCount() == 1 && hasStatus(snapshot, booked.id(), AppointmentStatus.WAITING));
        assertSnapshotIsFor(bookingUpdate, doctor.getId());

        AppointmentResponse called = http.exchange(url("/api/doctor/queue/next"), HttpMethod.POST,
                authorized(token(doctorUser), null), AppointmentResponse.class).getBody();
        assertNotNull(called);
        QueueUpdateResponse callUpdate = await(validHandler.messages, snapshot -> hasStatus(snapshot, booked.id(), AppointmentStatus.CALLED));
        assertSnapshotIsFor(callUpdate, doctor.getId());

        http.exchange(url("/api/doctor/queue/" + booked.id() + "/complete"), HttpMethod.POST,
                authorized(token(doctorUser), null), AppointmentResponse.class);
        QueueUpdateResponse completeUpdate = await(validHandler.messages, snapshot -> hasStatus(snapshot, booked.id(), AppointmentStatus.DONE));
        assertSnapshotIsFor(completeUpdate, doctor.getId());

        AppointmentResponse secondBooking = http.exchange(url("/api/appointments"), HttpMethod.POST,
                authorized(token(patient), new BookingRequest(doctor.getId(), LocalDate.now(), null, true)), AppointmentResponse.class)
                .getBody();
        assertNotNull(secondBooking);
        QueueUpdateResponse secondBookingUpdate = await(validHandler.messages,
                snapshot -> hasStatus(snapshot, secondBooking.id(), AppointmentStatus.WAITING));
        assertSnapshotIsFor(secondBookingUpdate, doctor.getId());

        http.exchange(url("/api/doctor/queue/" + secondBooking.id() + "/skip"), HttpMethod.POST,
                authorized(token(doctorUser), null), AppointmentResponse.class);
        QueueUpdateResponse skipUpdate = await(validHandler.messages,
                snapshot -> hasStatus(snapshot, secondBooking.id(), AppointmentStatus.SKIPPED));
        assertSnapshotIsFor(skipUpdate, doctor.getId());
    }

    @AfterEach
    void stopClient() {
        sessions.forEach(session -> { if (session.isConnected()) session.disconnect(); });
        if (stompClient != null) stompClient.stop();
        scheduler.shutdown();
    }

    private void startClient() {
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("stomp-test-");
        scheduler.initialize();
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        stompClient.setTaskScheduler(scheduler);
        stompClient.setReceiptTimeLimit(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
    }

    private StompSession connect(String jwtToken, SessionHandler handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwtToken);
        StompSession session = stompClient.connectAsync("http://127.0.0.1:" + port + "/ws", new WebSocketHttpHeaders(), headers, handler)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private User saveUser(String name, String email, Role role) {
        return users.saveAndFlush(new User(name, email, "integration-test", role, null));
    }

    private String token(User user) {
        var principal = org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password("unused").roles(user.getRole().name()).build();
        return jwt.generateToken(principal);
    }

    private HttpEntity<?> authorized(String jwtToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }

    private QueueUpdateResponse await(BlockingQueue<QueueUpdateResponse> messages, Predicate<QueueUpdateResponse> matches) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < end) {
            QueueUpdateResponse response = messages.poll(250, TimeUnit.MILLISECONDS);
            if (response != null && matches.test(response)) return response;
        }
        throw new AssertionError("Expected queue snapshot was not delivered before timeout");
    }

    private boolean hasStatus(QueueUpdateResponse snapshot, Long appointmentId, AppointmentStatus status) {
        return snapshot.queue().stream().anyMatch(entry -> entry.appointmentId().equals(appointmentId) && entry.status() == status);
    }

    private void assertSnapshotIsFor(QueueUpdateResponse snapshot, Long doctorId) {
        assertThat(snapshot.doctorId()).isEqualTo(doctorId);
        assertThat(snapshot.date()).isEqualTo(LocalDate.now());
    }

    private void awaitSubscription(String username, String destination) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < end) {
            var user = userRegistry.getUser(username);
            if (user != null && user.getSessions().stream().flatMap(session -> session.getSubscriptions().stream())
                    .anyMatch(subscription -> destination.equals(subscription.getDestination()))) return;
            Thread.sleep(25);
        }
        throw new AssertionError("STOMP broker did not register the authenticated queue subscription");
    }

    private static final class SessionHandler extends StompSessionHandlerAdapter {
        final BlockingQueue<QueueUpdateResponse> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<Object> errorFrames = new LinkedBlockingQueue<>();
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            errorFrames.offer(payload == null ? headers : payload);
        }
        @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                              byte[] payload, Throwable exception) {
            errorFrames.offer(exception);
        }
    }

    private static final class SnapshotHandler implements StompFrameHandler {
        private final BlockingQueue<QueueUpdateResponse> messages;
        private SnapshotHandler(BlockingQueue<QueueUpdateResponse> messages) { this.messages = messages; }
        @Override public Type getPayloadType(StompHeaders headers) { return QueueUpdateResponse.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { messages.offer((QueueUpdateResponse) payload); }
    }
}
