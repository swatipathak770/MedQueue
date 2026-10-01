package com.medqueue.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Pattern(regexp = "^$|^[+0-9()\\- ]{7,30}$", message = "Phone number is invalid") String phone) { }
