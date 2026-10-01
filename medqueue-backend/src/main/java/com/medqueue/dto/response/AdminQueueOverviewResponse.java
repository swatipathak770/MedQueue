package com.medqueue.dto.response;

import com.medqueue.entity.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;

/** A patient-safe summary for the administrator's cross-doctor queue view. */
public record AdminQueueOverviewResponse(
        Long doctorId,
        String doctorName,
        String department,
        LocalDate date,
        boolean available,
        boolean queueOpen,
        int currentTokenNumber,
        long waitingCount,
        Integer activeTokenNumber,
        AppointmentStatus activeStatus,
        Instant updatedAt) { }
