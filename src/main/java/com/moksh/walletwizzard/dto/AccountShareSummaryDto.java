package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AccountShareSummaryDto(
        UUID accountId,
        String accountName,
        String accountType,
        BigDecimal currentBalance,
        List<ParticipantShareView> participants
) {
    public record ParticipantShareView(
            UUID participantId,
            UUID personId,
            String personName,
            BigDecimal sharePercent,
            /** currentBalance × sharePercent / 100 — their share of the outstanding balance. */
            BigDecimal outstandingShare,
            String notes
    ) {}
}
