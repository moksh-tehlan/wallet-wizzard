package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.EntrySide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LineRequest(
        @NotNull UUID accountId,

        /** Must be strictly positive — sign is expressed by side, not amount. */
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,

        @NotNull EntrySide side,

        String memo
) {}
