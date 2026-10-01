package com.medqueue.service;

import com.medqueue.dto.response.DoctorAnalyticsResponse;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.QueueStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final QueueStateRepository queueStates;
    public AnalyticsService(AppointmentRepository appointments, DoctorRepository doctors, QueueStateRepository queueStates) {
        this.appointments = appointments; this.doctors = doctors; this.queueStates = queueStates;
    }
    @Transactional(readOnly = true)
    public List<DoctorAnalyticsResponse> today() {
        LocalDate date = LocalDate.now();
        Map<Long, List<com.medqueue.entity.Appointment>> byDoctor = new HashMap<>();
        appointments.findByAppointmentDateOrderByDoctor_IdAscTokenNumberAsc(date).forEach(a -> byDoctor.computeIfAbsent(a.getDoctor().getId(), k -> new ArrayList<>()).add(a));
        Map<Long, Boolean> queueOpenByDoctor = queueStates.findByQueueDate(date).stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getDoctor().getId(), s -> s.isOpen()));
        return doctors.findAllByOrderByUserNameAsc().stream().map(d -> {
            var rows = byDoctor.getOrDefault(d.getId(), List.of());
            long waiting = rows.stream().filter(a -> a.getStatus() == AppointmentStatus.WAITING).count();
            var served = rows.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE && a.getCalledAt() != null).toList();
            long averageWait = served.isEmpty() ? 0 : served.stream().mapToLong(a -> Math.max(0, Duration.between(a.getCreatedAt(), a.getCalledAt()).toMinutes())).sum() / served.size();
            long noShows = rows.stream().filter(a -> a.getStatus() == AppointmentStatus.SKIPPED).count();
            long outcomes = noShows + served.size();
            double noShowRate = outcomes == 0 ? 0 : Math.round(noShows * 1000.0 / outcomes) / 10.0;
            boolean open = queueOpenByDoctor.getOrDefault(d.getId(), true);
            return new DoctorAnalyticsResponse(d.getId(), d.getUser().getName(), waiting, served.size(), averageWait, noShows, noShowRate, d.isAvailable(), open);
        }).toList();
    }
}
