package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GroupSummaryDto(
        UUID groupId,
        String groupName,
        int memberCount,
        BigDecimal totalExpenses,
        String notes
) {}
