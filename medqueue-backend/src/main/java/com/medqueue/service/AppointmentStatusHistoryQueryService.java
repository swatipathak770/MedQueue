package com.medqueue.service;

import com.medqueue.dto.response.AppointmentStatusHistoryResponse;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.AppointmentStatusHistoryRepository;
import com.medqueue.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AppointmentStatusHistoryQueryService {
    private final AppointmentRepository appointments;
    private final AppointmentStatusHistoryRepository history;
    public AppointmentStatusHistoryQueryService(AppointmentRepository appointments, AppointmentStatusHistoryRepository history) {
        this.appointments = appointments; this.history = history;
    }

    @Transactional(readOnly = true)
    public List<AppointmentStatusHistoryResponse> forAppointment(Long appointmentId, Authentication authentication) {
        var appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        boolean allowed = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"))
                    && appointment.getPatient().getEmail().equals(authentication.getName()))
                || (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"))
                    && appointment.getDoctor().getUser().getEmail().equals(authentication.getName()));
        if (!allowed) throw new AccessDeniedException("Appointment is not accessible to this user");
        return history.findByAppointmentIdOrderByChangedAtAscIdAsc(appointmentId).stream()
                .map(AppointmentStatusHistoryResponse::from).toList();
    }
}
