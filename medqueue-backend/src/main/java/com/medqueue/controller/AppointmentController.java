package com.medqueue.controller;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.dto.response.AppointmentResponse;
import com.medqueue.entity.Appointment;
import com.medqueue.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointments;
    public AppointmentController(AppointmentService appointments) { this.appointments = appointments; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PATIENT')")
    public AppointmentResponse book(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        Appointment a = appointments.book(authentication.getName(), request);
        long position = appointments.position(a);
        long wait = appointments.estimatedWaitMinutes(a, position);
        return AppointmentResponse.from(a, position, wait);
    }
    @GetMapping("/me") @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentResponse> history(Authentication authentication) {
        return appointments.history(authentication.getName()).stream().map(a -> {
            long position = appointments.position(a);
            return AppointmentResponse.from(a, position, appointments.estimatedWaitMinutes(a, position));
        }).toList();
    }
}
