package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.LoanDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only view of a loan with its live outstanding balance.
 * outstandingBalance is the current balance of the loan account (always computed).
 */
public record LoanSummary(
        UUID id,
        String name,
        LoanDirection direction,
        BigDecimal principal,
        BigDecimal outstandingBalance,
        BigDecimal interestRate,
        BigDecimal emiAmount,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {
    public boolean isFullyRepaid() {
        return outstandingBalance.compareTo(BigDecimal.ZERO) == 0;
    }
}
