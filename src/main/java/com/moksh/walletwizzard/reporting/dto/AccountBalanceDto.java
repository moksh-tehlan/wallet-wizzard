package com.moksh.walletwizzard.reporting.dto;

import com.moksh.walletwizzard.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceDto(
        UUID accountId,
        String accountName,
        AccountType type,
        BigDecimal balance
) {}
