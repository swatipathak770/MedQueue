package com.medqueue.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record DepartmentRequest(@NotBlank @Size(max = 100) String name) { }
