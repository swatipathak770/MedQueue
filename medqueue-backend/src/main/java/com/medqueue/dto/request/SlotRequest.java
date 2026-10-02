package com.medqueue.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record SlotRequest(@NotNull @Positive Long doctorId, @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime, @Positive int slotCapacity) { }
