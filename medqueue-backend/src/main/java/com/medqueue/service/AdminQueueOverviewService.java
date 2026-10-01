package com.medqueue.service;

import com.medqueue.dto.response.AdminQueueOverviewResponse;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminQueueOverviewService {
    private final DoctorRepository doctors;
    private final QueueSnapshotReader snapshots;

    public AdminQueueOverviewService(DoctorRepository doctors, QueueSnapshotReader snapshots) {
        this.doctors = doctors;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public List<AdminQueueOverviewResponse> today() {
        LocalDate today = LocalDate.now();
        return doctors.findAllByOrderByUserNameAsc().stream().map(doctor -> {
            var snapshot = snapshots.snapshot(doctor.getId(), today);
            var active = snapshot.queue().stream()
                    .filter(entry -> entry.status() == AppointmentStatus.CALLED
                            || entry.status() == AppointmentStatus.IN_PROGRESS)
                    .findFirst().orElse(null);
            return new AdminQueueOverviewResponse(doctor.getId(), doctor.getUser().getName(),
                    doctor.getDepartment().getName(), today, snapshot.available(), snapshot.open(),
                    snapshot.currentTokenNumber(), snapshot.waitingCount(),
                    active == null ? null : active.tokenNumber(),
                    active == null ? null : active.status(), snapshot.updatedAt());
        }).toList();
    }
}
