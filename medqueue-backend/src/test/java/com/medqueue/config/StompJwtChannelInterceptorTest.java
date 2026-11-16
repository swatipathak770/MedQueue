package com.medqueue.config;

import com.medqueue.entity.Doctor;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.security.JwtService;
import com.medqueue.service.MedQueueUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompJwtChannelInterceptorTest {
    private final String secret = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
    private final JwtService jwt = new JwtService(secret, 3600);
    private final MedQueueUserDetailsService userDetails = mock(MedQueueUserDetailsService.class);
    private final DoctorRepository doctors = mock(DoctorRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(jwt, userDetails, doctors, appointments);

    @Test void authenticatesConnectAndAllowsOnlyTheDoctorOwnQueue() {
        var doctorPrincipal = User.withUsername("doctor@example.test").password("n/a").roles("DOCTOR").build();
        when(userDetails.loadUserByUsername("doctor@example.test")).thenReturn(doctorPrincipal);
        Doctor profile = mock(Doctor.class); when(profile.getId()).thenReturn(12L);
        when(doctors.findByUserEmail("doctor@example.test")).thenReturn(Optional.of(profile));

        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("Authorization", "Bearer " + jwt.generateToken(doctorPrincipal));
        interceptor.preSend(message(connect), mock(org.springframework.messaging.MessageChannel.class));
        assertNotNull(connect.getUser());

        StompHeaderAccessor ownTopic = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        ownTopic.setDestination("/topic/queue/12"); ownTopic.setUser(connect.getUser());
        assertNotNull(interceptor.preSend(message(ownTopic), mock(org.springframework.messaging.MessageChannel.class)));
        StompHeaderAccessor otherTopic = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        otherTopic.setDestination("/topic/queue/13"); otherTopic.setUser(connect.getUser());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(otherTopic), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test void refusesMissingTokensAndClientPublishedMessages() {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(connect), mock(org.springframework.messaging.MessageChannel.class)));
        StompHeaderAccessor malformed = StompHeaderAccessor.create(StompCommand.CONNECT);
        malformed.addNativeHeader("Authorization", "Bearer not.a.jwt");
        assertThrows(RuntimeException.class, () -> interceptor.preSend(message(malformed), mock(org.springframework.messaging.MessageChannel.class)));
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/queue/12");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(send), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test void patientMaySubscribeOnlyWithAnEligibleAppointmentForThatDoctor() {
        var patient = principal("patient@example.test", "PATIENT");
        when(appointments.existsByPatient_EmailAndDoctor_IdAndStatusIn("patient@example.test", 41L,
                List.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS)))
                .thenReturn(true);

        assertSubscriptionAllowed(patient, "/topic/queue/41");
        verify(appointments).existsByPatient_EmailAndDoctor_IdAndStatusIn("patient@example.test", 41L,
                List.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS));
        verifyNoInteractions(doctors);
    }

    @Test void patientWithoutAnEligibleAppointmentIsDenied() {
        var patient = principal("patient@example.test", "PATIENT");
        when(appointments.existsByPatient_EmailAndDoctor_IdAndStatusIn(eq("patient@example.test"), eq(41L), anyList()))
                .thenReturn(false);

        assertSubscriptionDenied(patient, "/topic/queue/41");
    }

    @Test void patientAppointmentForAnotherDoctorDoesNotAuthorizeSubscription() {
        var patient = principal("patient@example.test", "PATIENT");
        when(appointments.existsByPatient_EmailAndDoctor_IdAndStatusIn(eq("patient@example.test"), eq(42L), anyList()))
                .thenReturn(false);

        assertSubscriptionDenied(patient, "/topic/queue/42");
        verify(appointments).existsByPatient_EmailAndDoctor_IdAndStatusIn("patient@example.test", 42L,
                List.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS));
    }

    @Test void adminMaySubscribeToAnyQueueWithoutRelationshipQueries() {
        var admin = principal("admin@example.test", "ADMIN");

        assertSubscriptionAllowed(admin, "/topic/queue/987");
        verifyNoInteractions(doctors, appointments);
    }

    @Test void rejectsUnauthenticatedAndMalformedSubscriptions() {
        StompHeaderAccessor unauthenticated = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        unauthenticated.setDestination("/topic/queue/41");
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(unauthenticated), mock(org.springframework.messaging.MessageChannel.class)));

        var patient = principal("patient@example.test", "PATIENT");
        for (String destination : List.of("/topic/queue/", "/topic/queue/0", "/topic/queue/041",
                "/topic/queue/41/", "/topic/queue/41/extra", "/topic/queue/abc",
                "/topic/queue/9223372036854775808")) {
            StompHeaderAccessor malformed = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            malformed.setDestination(destination); malformed.setUser(patient);
            assertThrows(IllegalArgumentException.class,
                    () -> interceptor.preSend(message(malformed), mock(org.springframework.messaging.MessageChannel.class)), destination);
        }
        verifyNoInteractions(doctors, appointments);
    }

    private org.springframework.security.core.Authentication principal(String email, String role) {
        var details = User.withUsername(email).password("n/a").roles(role).build();
        when(userDetails.loadUserByUsername(email)).thenReturn(details);
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("Authorization", "Bearer " + jwt.generateToken(details));
        interceptor.preSend(message(connect), mock(org.springframework.messaging.MessageChannel.class));
        return (org.springframework.security.core.Authentication) connect.getUser();
    }

    private void assertSubscriptionAllowed(java.security.Principal principal, String destination) {
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination(destination); subscribe.setUser(principal);
        assertNotNull(interceptor.preSend(message(subscribe), mock(org.springframework.messaging.MessageChannel.class)));
    }

    private void assertSubscriptionDenied(java.security.Principal principal, String destination) {
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination(destination); subscribe.setUser(principal);
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(subscribe), mock(org.springframework.messaging.MessageChannel.class)));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
