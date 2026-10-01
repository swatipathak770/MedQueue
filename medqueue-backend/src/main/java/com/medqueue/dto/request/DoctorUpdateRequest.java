package com.medqueue.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
public record DoctorUpdateRequest(@NotNull Long departmentId, @NotBlank @Size(max = 120) String specialization,
        @Positive int avgConsultMinutes) { }
