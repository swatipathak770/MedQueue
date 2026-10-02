package com.medqueue.config;

import com.medqueue.entity.Doctor;
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
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompJwtChannelInterceptorTest {
    private final String secret = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
    private final JwtService jwt = new JwtService(secret, 3600);
    private final MedQueueUserDetailsService userDetails = mock(MedQueueUserDetailsService.class);
    private final DoctorRepository doctors = mock(DoctorRepository.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(jwt, userDetails, doctors);

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

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
