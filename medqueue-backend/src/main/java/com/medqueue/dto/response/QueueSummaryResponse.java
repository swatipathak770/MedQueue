package com.medqueue.dto.response;
import java.time.LocalDate;
import java.util.List;
public record QueueSummaryResponse(Long doctorId, LocalDate date, boolean available, boolean open,
        int currentTokenNumber, List<AppointmentResponse> appointments) { }
