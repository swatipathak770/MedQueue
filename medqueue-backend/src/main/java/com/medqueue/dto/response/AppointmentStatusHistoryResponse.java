package com.medqueue.dto.response;

import com.medqueue.entity.AppointmentStatus;
import com.medqueue.entity.AppointmentStatusHistory;
import java.time.Instant;

public record AppointmentStatusHistoryResponse(Long id, AppointmentStatus oldStatus, AppointmentStatus newStatus,
                                               Instant changedAt, String changedBy) {
    public static AppointmentStatusHistoryResponse from(AppointmentStatusHistory entry) {
        return new AppointmentStatusHistoryResponse(entry.getId(), entry.getOldStatus(), entry.getNewStatus(), entry.getChangedAt(), entry.getChangedBy());
    }
}
