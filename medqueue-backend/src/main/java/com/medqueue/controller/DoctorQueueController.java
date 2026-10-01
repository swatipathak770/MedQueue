package com.medqueue.controller;

import com.medqueue.dto.request.AvailabilityRequest;
import com.medqueue.dto.response.AppointmentResponse;
import com.medqueue.dto.response.QueueSummaryResponse;
import com.medqueue.entity.Appointment;
import com.medqueue.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/doctor/queue")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorQueueController {
    private final QueueService queues;
    public DoctorQueueController(QueueService queues) { this.queues = queues; }
    @GetMapping public QueueSummaryResponse today(Authentication auth) { return queues.today(auth.getName()); }
    @PostMapping("/next") @ResponseStatus(HttpStatus.OK)
    public AppointmentResponse callNext(Authentication auth) { return response(queues.callNext(auth.getName())); }
    @PostMapping("/{id}/complete") public AppointmentResponse complete(Authentication auth, @PathVariable Long id) {
        return response(queues.complete(auth.getName(), id));
    }
    @PostMapping("/{id}/skip") public AppointmentResponse skip(Authentication auth, @PathVariable Long id) {
        return response(queues.skip(auth.getName(), id));
    }
    @PutMapping("/availability") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void availability(Authentication auth, @Valid @RequestBody AvailabilityRequest request) {
        queues.setAvailability(auth.getName(), request.available());
    }
    @PostMapping("/close") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(Authentication auth) { queues.closeQueue(auth.getName()); }
    private AppointmentResponse response(Appointment a) {
        return AppointmentResponse.from(a, 0, 0);
    }
}
