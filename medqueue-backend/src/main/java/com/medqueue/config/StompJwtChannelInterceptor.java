package com.medqueue.config;

import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.security.JwtService;
import com.medqueue.service.MedQueueUserDetailsService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {
    private static final Pattern QUEUE_DESTINATION = Pattern.compile("^/topic/queue/([1-9][0-9]*)$");
    private static final List<AppointmentStatus> ELIGIBLE_PATIENT_APPOINTMENT_STATUSES =
            List.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS);
    private final JwtService jwt;
    private final MedQueueUserDetailsService users;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    public StompJwtChannelInterceptor(JwtService jwt, MedQueueUserDetailsService users, DoctorRepository doctors,
                                      AppointmentRepository appointments) {
        this.jwt = jwt; this.users = users; this.doctors = doctors; this.appointments = appointments;
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
            Matcher match = destination == null ? null : QUEUE_DESTINATION.matcher(destination);
            if (match == null || !match.matches())
                throw new IllegalArgumentException("Only queue subscriptions with a positive doctor ID are allowed");
            long doctorId;
            try {
                doctorId = Long.parseLong(match.group(1));
            } catch (NumberFormatException malformedId) {
                throw new IllegalArgumentException("Queue destination contains an invalid doctor ID", malformedId);
            }
            authorizeQueueSubscription(principal, doctorId);
        }
        return message;
    }

    private void authorizeQueueSubscription(java.security.Principal principal, long doctorId) {
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated())
            throw new org.springframework.security.access.AccessDeniedException("Authenticated queue subscription required");

        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) return;

        boolean doctorRole = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        if (doctorRole) {
            boolean ownsQueue = doctors.findByUserEmail(authentication.getName())
                    .map(doctor -> doctor.getId().equals(doctorId)).orElse(false);
            if (!ownsQueue) throw new org.springframework.security.access.AccessDeniedException("Doctor may only subscribe to their queue");
            return;
        }

        boolean patientRole = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
        if (patientRole && appointments.existsByPatient_EmailAndDoctor_IdAndStatusIn(
                authentication.getName(), doctorId, ELIGIBLE_PATIENT_APPOINTMENT_STATUSES)) return;
        throw new org.springframework.security.access.AccessDeniedException("Queue subscription is not allowed");
    }
}
