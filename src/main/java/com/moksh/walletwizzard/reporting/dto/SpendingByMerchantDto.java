package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SpendingByMerchantDto(
        String merchant,
        BigDecimal amount,
        int transactionCount,
        double percentageOfTotal
) {
    public static SpendingByMerchantDto of(String merchant, BigDecimal amount,
                                           int count, BigDecimal total) {
        double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : amount.multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP)
                        .doubleValue();
        return new SpendingByMerchantDto(merchant, amount, count, pct);
    }
}
