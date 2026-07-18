package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.DebtDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only view of a debt record with its live outstanding balance.
 * Balance is computed from journal entry lines — not stored.
 * A balance of zero means the debt is fully settled.
 */
public record DebtSummary(
        UUID id,
        UUID personId,
        String personName,
        DebtDirection direction,
        BigDecimal originalAmount,
        BigDecimal outstandingBalance,
        LocalDate dueDate,
        String description
) {
    public boolean isSettled() {
        return outstandingBalance.compareTo(BigDecimal.ZERO) == 0;
    }
}
