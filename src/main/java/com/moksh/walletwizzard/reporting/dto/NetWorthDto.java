package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthDto(
        LocalDate asOfDate,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        /** Co-borrower share of outstanding loan balance (offsets your liability). */
        BigDecimal sharedLoanReceivables,
        /**
         * Investment market value gains: SUM(currentValue − investedAmount) across all investments.
         * Negative when portfolio is in loss. Added on top of account-based assets.
         */
        BigDecimal investmentGains,
        BigDecimal netWorth  // totalAssets − totalLiabilities + sharedLoanReceivables + investmentGains
) {
    public static NetWorthDto of(LocalDate asOfDate,
                                 BigDecimal assets,
                                 BigDecimal liabilities,
                                 BigDecimal sharedLoanReceivables,
                                 BigDecimal investmentGains) {
        return new NetWorthDto(
                asOfDate, assets, liabilities, sharedLoanReceivables, investmentGains,
                assets.subtract(liabilities).add(sharedLoanReceivables).add(investmentGains)
        );
    }
}
