package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthDto(
        LocalDate asOfDate,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        /**
         * Co-borrower share of the current outstanding loan balance for all TAKEN shared loans.
         * = remaining principal on each loan × participant sharePercent.
         * Offsets the portion of your loan liabilities that co-borrowers are responsible for.
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
