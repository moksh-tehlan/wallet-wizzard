package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record BudgetStatusDto(
        UUID budgetId,
        UUID accountId,
        String accountName,
        BigDecimal budgetAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        double percentageUsed,
        String status   // ON_TRACK | WARNING | EXCEEDED
) {
    public static BudgetStatusDto of(UUID budgetId, UUID accountId, String accountName,
                                     BigDecimal budget, BigDecimal spent) {
        BigDecimal remaining = budget.subtract(spent);
        double pct = budget.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : spent.multiply(BigDecimal.valueOf(100))
                        .divide(budget, 1, RoundingMode.HALF_UP)
                        .doubleValue();
        String status = pct >= 100 ? "EXCEEDED" : pct >= 80 ? "WARNING" : "ON_TRACK";
        return new BudgetStatusDto(budgetId, accountId, accountName, budget, spent, remaining, pct, status);
    }
}
