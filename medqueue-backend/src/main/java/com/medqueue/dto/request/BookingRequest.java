package com.medqueue.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
public record BookingRequest(@NotNull @Positive Long doctorId,
                             @NotNull @FutureOrPresent LocalDate appointmentDate,
                             @Positive Long slotId, boolean walkIn) { }
