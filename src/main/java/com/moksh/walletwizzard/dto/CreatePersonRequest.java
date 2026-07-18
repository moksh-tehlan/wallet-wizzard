package com.moksh.walletwizzard.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePersonRequest(
        @NotBlank String name,
        String phone,
        String email,
        String notes
) {}
