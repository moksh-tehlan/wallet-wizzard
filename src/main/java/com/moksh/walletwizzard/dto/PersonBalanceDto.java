package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.DebtDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PersonBalanceDto(
        UUID personId,
        String personName,
        List<DirectDebtView> directDebts,
        List<LoanParticipationView> loanParticipations,
        List<AccountParticipationView> accountParticipations,
        /** Positive = person owes you; negative = you owe person */
        BigDecimal netBalance
) {
    public record DirectDebtView(
            UUID debtId,
            DebtDirection direction,
            String description,
            BigDecimal originalAmount,
            BigDecimal outstandingBalance,
            LocalDate dueDate
    ) {}

    public record LoanParticipationView(
            UUID loanId,
            String loanName,
            BigDecimal sharePercent,
            BigDecimal paidByUser,
            BigDecimal currentlyDue,
            BigDecimal scheduledFuture
    ) {}

    /**
     * Share of any non-loan account (credit card, informal liability, etc.).
     * outstandingShare = currentBalance × sharePercent — what they owe you right now.
     */
    public record AccountParticipationView(
            UUID accountId,
            String accountName,
            String accountType,
            BigDecimal sharePercent,
            BigDecimal outstandingShare
    ) {}
}
