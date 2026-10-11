package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowProjectionDto(
        BigDecimal currentBalance,
        List<MonthProjection> months
) {
    public record MonthProjection(
            String month,               // YYYY-MM
            BigDecimal openingBalance,
            BigDecimal totalInflows,
            BigDecimal totalOutflows,
            BigDecimal closingBalance,
            List<String> inflowItems,
            List<String> outflowItems
    ) {}
}
