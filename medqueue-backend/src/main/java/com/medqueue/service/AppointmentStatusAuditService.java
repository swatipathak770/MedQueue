package com.medqueue.service;

import com.medqueue.entity.Appointment;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.entity.AppointmentStatusHistory;
import com.medqueue.repository.AppointmentStatusHistoryRepository;
import org.springframework.stereotype.Service;

@Service
public class AppointmentStatusAuditService {
    private final AppointmentStatusHistoryRepository history;
    public AppointmentStatusAuditService(AppointmentStatusHistoryRepository history) { this.history = history; }

    public void recordInitial(Appointment appointment, Long actorUserId) {
        if (appointment.getStatus() != AppointmentStatus.WAITING)
            throw new IllegalStateException("New appointments must start in WAITING status");
        history.save(new AppointmentStatusHistory(appointment, null, AppointmentStatus.WAITING, actor(actorUserId)));
    }

    public void transition(Appointment appointment, AppointmentStatus next, Long actorUserId) {
        AppointmentStatus previous = appointment.getStatus();
        if (previous == next) return;
        appointment.transitionTo(next);
        history.save(new AppointmentStatusHistory(appointment, previous, next, actor(actorUserId)));
    }

    private String actor(Long userId) { return userId == null ? "SYSTEM" : "USER:" + userId; }
}
