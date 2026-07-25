package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupBalanceDto(
        UUID groupId,
        String groupName,
        List<MemberBalance> members
) {
    public record MemberBalance(
            UUID personId,
            String personName,
            BigDecimal theyOweYou,
            BigDecimal youOweThem,
            BigDecimal net
    ) {}
}
