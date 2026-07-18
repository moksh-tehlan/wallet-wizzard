package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        AccountSubType subType,
        UUID parentId,
        String notes
) {}
