package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GroupExpenseDto(
        UUID expenseId,
        String description,
        BigDecimal totalAmount,
        String paidBy,
        LocalDate date,
        List<SplitView> splits
) {
    public record SplitView(
            UUID personId,
            String personName,
            BigDecimal shareAmount,
            boolean isSettled
    ) {}
}
