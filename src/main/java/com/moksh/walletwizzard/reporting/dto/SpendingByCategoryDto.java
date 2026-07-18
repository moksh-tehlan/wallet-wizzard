package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record SpendingByCategoryDto(
        UUID accountId,
        String accountName,
        BigDecimal amount,
        double percentageOfTotal   // 0.0–100.0; computed after the query
) {
    public static SpendingByCategoryDto of(UUID accountId, String name,
                                           BigDecimal amount, BigDecimal total) {
        double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : amount.multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP)
                        .doubleValue();
        return new SpendingByCategoryDto(accountId, name, amount, pct);
    }
}
