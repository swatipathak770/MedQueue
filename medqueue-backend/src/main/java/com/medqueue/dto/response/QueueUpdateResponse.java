package com.medqueue.dto.response;
import com.medqueue.entity.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record QueueUpdateResponse(Long doctorId, LocalDate date, Instant updatedAt, boolean available, boolean open,
        int currentTokenNumber, long waitingCount, List<QueueEntry> queue) {
    public record QueueEntry(Long appointmentId, int tokenNumber, AppointmentStatus status, long position, long estimatedWaitMinutes) { }
}
