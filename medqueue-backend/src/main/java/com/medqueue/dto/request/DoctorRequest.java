package com.medqueue.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
public record DoctorRequest(@NotBlank @Size(max = 120) String name, @NotBlank @jakarta.validation.constraints.Email @Size(max = 190) String email,
        @NotBlank @Size(min = 8, max = 72) String password, @Size(max = 30) String phone, @NotNull Long departmentId,
        @NotBlank @Size(max = 120) String specialization, @Positive int avgConsultMinutes) { }
