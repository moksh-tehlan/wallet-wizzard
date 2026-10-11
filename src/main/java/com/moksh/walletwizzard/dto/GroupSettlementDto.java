package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupSettlementDto(
        UUID groupId,
        String groupName,
        BigDecimal totalExpenses,
        BigDecimal perPersonShare,
        List<Payment> settlements
) {
    public record Payment(
            String from,
            UUID fromPersonId,
            String to,
            UUID toPersonId,
            BigDecimal amount
    ) {}
}
