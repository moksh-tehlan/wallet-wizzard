package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LoanShareSummaryDto(
        UUID loanId,
        String loanName,
        BigDecimal totalScheduled,
        List<ParticipantShareDto> participants
) {
    public record ParticipantShareDto(
            UUID participantId,
            UUID personId,
            String personName,
            BigDecimal sharePercent,
            BigDecimal totalShareAmount,
            BigDecimal paidShareAmount,
            BigDecimal dueShareAmount,
            BigDecimal remainingShareAmount
    ) {}
}
