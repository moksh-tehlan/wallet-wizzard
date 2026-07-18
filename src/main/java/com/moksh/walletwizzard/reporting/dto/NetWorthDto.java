package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthDto(
        LocalDate asOfDate,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal netWorth         // totalAssets − totalLiabilities
) {
    public static NetWorthDto of(LocalDate asOfDate,
                                 BigDecimal assets, BigDecimal liabilities) {
        return new NetWorthDto(asOfDate, assets, liabilities, assets.subtract(liabilities));
    }
}
