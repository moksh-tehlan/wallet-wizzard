package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyReportDto(
        int year,
        int month,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings,
        List<SpendingByCategoryDto> spendingByCategory
) {}
