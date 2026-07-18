package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowDto(
        LocalDate from,
        LocalDate to,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings   // totalIncome − totalExpenses; negative = overspent
) {
    public static CashFlowDto of(LocalDate from, LocalDate to,
                                 BigDecimal income, BigDecimal expenses) {
        return new CashFlowDto(from, to, income, expenses, income.subtract(expenses));
    }
}
