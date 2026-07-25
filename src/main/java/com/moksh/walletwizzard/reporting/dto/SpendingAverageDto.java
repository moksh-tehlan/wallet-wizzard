package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SpendingAverageDto(
        UUID accountId,
        String accountName,
        BigDecimal averageMonthlySpend,
        BigDecimal maxMonthSpend,
        BigDecimal minMonthSpend,
        int monthsWithData
) {}
