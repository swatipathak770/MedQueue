package com.medqueue.dto.request;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public record BookingRequest(@NotNull Long doctorId, @NotNull LocalDate appointmentDate, Long slotId, boolean walkIn) { }
