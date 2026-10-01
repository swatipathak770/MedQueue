package com.medqueue.config;

import com.medqueue.repository.DoctorRepository;
import com.medqueue.security.JwtService;
import com.medqueue.service.MedQueueUserDetailsService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {
    private final JwtService jwt;
    private final MedQueueUserDetailsService users;
    private final DoctorRepository doctors;
    public StompJwtChannelInterceptor(JwtService jwt, MedQueueUserDetailsService users, DoctorRepository doctors) {
        this.jwt = jwt; this.users = users; this.doctors = doctors;
    }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        if (StompCommand.SEND.equals(accessor.getCommand()))
            throw new org.springframework.security.access.AccessDeniedException("Clients cannot publish queue messages");
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("WebSocket authentication required");
            String token = header.substring(7);
            String email = jwt.extractUsername(token);
            var user = users.loadUserByUsername(email);
            if (!jwt.isTokenValid(token, user)) throw new IllegalArgumentException("Invalid WebSocket token");
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            var principal = accessor.getUser();
            String destination = accessor.getDestination();
            if (principal == null || destination == null || !destination.matches("/topic/queue/[0-9]+"))
                throw new IllegalArgumentException("Only authenticated queue subscriptions are allowed");
            var authentication = (org.springframework.security.core.Authentication) principal;
            boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean doctorRole = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            if (doctorRole) {
                Long id = Long.valueOf(destination.substring(destination.lastIndexOf('/') + 1));
                boolean ownsQueue = doctors.findByUserEmail(authentication.getName()).map(d -> d.getId().equals(id)).orElse(false);
                if (!ownsQueue) throw new org.springframework.security.access.AccessDeniedException("Doctor may only subscribe to their queue");
            } else if (!admin && authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_PATIENT"))) {
                throw new org.springframework.security.access.AccessDeniedException("Queue subscription is not allowed");
            }
        }
        return message;
    }
}
