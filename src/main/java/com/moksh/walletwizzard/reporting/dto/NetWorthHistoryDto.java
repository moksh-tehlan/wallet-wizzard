package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record NetWorthHistoryDto(
        List<SnapshotPoint> snapshots,
        BigDecimal changeAmount,    // latest - oldest in range
        double changePercent
) {
    public record SnapshotPoint(
            String date,
            BigDecimal netWorth,
            BigDecimal liquidNetWorth
    ) {}
}
