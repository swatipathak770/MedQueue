package com.medqueue.dto.response;
import com.medqueue.entity.Appointment;
import com.medqueue.entity.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
public record AppointmentResponse(Long id, Long doctorId, String doctorName, String department, LocalDate appointmentDate,
        Long slotId, LocalTime startTime, LocalTime endTime, int tokenNumber, AppointmentStatus status,
        long queuePosition, long estimatedWaitMinutes, Instant createdAt, Instant calledAt, Instant completedAt) {
    public static AppointmentResponse from(Appointment a, long position, long waitMinutes) {
        return new AppointmentResponse(a.getId(), a.getDoctor().getId(), a.getDoctor().getUser().getName(),
                a.getDoctor().getDepartment().getName(), a.getAppointmentDate(), a.getSlot() == null ? null : a.getSlot().getId(),
                a.getSlot() == null ? null : a.getSlot().getStartTime(), a.getSlot() == null ? null : a.getSlot().getEndTime(),
                a.getTokenNumber(), a.getStatus(), position, waitMinutes, a.getCreatedAt(), a.getCalledAt(), a.getCompletedAt());
    }
}
