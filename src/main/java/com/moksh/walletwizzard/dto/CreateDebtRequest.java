package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.DebtDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDebtRequest(
        @NotNull UUID personId,
        @NotNull DebtDirection direction,
        @NotNull @DecimalMin("0.01") BigDecimal amount,

        /** The bank/wallet account that money moved through. */
        @NotNull UUID bankAccountId,

        LocalDate date,
        LocalDate dueDate,
        String description
) {}
