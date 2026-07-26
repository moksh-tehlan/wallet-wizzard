package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthDto(
        LocalDate asOfDate,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        /**
         * Sum of PAID installment amounts × co-borrower share% for all shared loans.
         * This offsets the portion of your loan liabilities that co-borrowers owe back to you.
         */
        BigDecimal sharedLoanReceivables,
        BigDecimal netWorth  // totalAssets − totalLiabilities + sharedLoanReceivables
) {
    public static NetWorthDto of(LocalDate asOfDate,
                                 BigDecimal assets,
                                 BigDecimal liabilities,
                                 BigDecimal sharedLoanReceivables) {
        return new NetWorthDto(
                asOfDate, assets, liabilities, sharedLoanReceivables,
                assets.subtract(liabilities).add(sharedLoanReceivables)
        );
    }
}
