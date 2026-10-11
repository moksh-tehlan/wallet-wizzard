package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialHealthDto(
        int overallScore,               // 0–100
        String grade,                   // A / B / C / D / F
        EmergencyFund emergencyFund,
        SavingsRate savingsRate,
        DebtBurden debtBurden,
        List<String> recommendations
) {
    public record EmergencyFund(
            BigDecimal cashAndBanks,
            BigDecimal monthlyExpenses,
            double monthsCovered,
            String status   // CRITICAL(<1m) / LOW(1-3m) / ADEQUATE(3-6m) / STRONG(>6m)
    ) {}

    public record SavingsRate(
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            double ratePercent,
            String status   // LOW(<10%) / MODERATE(10-20%) / GOOD(20-30%) / EXCELLENT(>30%)
    ) {}

    public record DebtBurden(
            BigDecimal monthlyEmiTotal,
            BigDecimal monthlyIncome,
            double debtToIncomePercent,
            String status   // HEALTHY(<35%) / MODERATE(35-50%) / HIGH(>50%)
    ) {}
}
