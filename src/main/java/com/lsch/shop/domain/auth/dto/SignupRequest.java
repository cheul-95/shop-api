package com.lsch.shop.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(@Email @NotBlank @Size(max = 100) String email,
                            @NotBlank @Size(min = 8, max = 64) String password,
                            @NotBlank @Size(max = 50) String name) {
}
