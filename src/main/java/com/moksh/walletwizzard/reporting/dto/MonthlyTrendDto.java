package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyTrendDto(
        int monthsRequested,
        BigDecimal averageMonthlyIncome,
        BigDecimal averageMonthlyExpenses,
        BigDecimal averageMonthlySavings,
        List<MonthRow> months
) {
    public record MonthRow(
            String month,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal netSavings
    ) {}
}
