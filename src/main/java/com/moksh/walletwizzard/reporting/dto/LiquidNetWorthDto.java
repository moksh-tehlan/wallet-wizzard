package com.moksh.walletwizzard.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LiquidNetWorthDto(
        LocalDate asOfDate,
        BigDecimal cashAndBanks,
        BigDecimal receivables,
        BigDecimal participantReceivables,
        BigDecimal investments,
        BigDecimal creditCardDebt,
        BigDecimal otherLiabilities,
        BigDecimal liquidNetWorth
) {
    public static LiquidNetWorthDto of(LocalDate date,
                                       BigDecimal cashAndBanks,
                                       BigDecimal receivables,
                                       BigDecimal participantReceivables,
                                       BigDecimal investments,
                                       BigDecimal creditCardDebt,
                                       BigDecimal otherLiabilities) {
        BigDecimal liquidNetWorth = cashAndBanks
                .add(receivables)
                .add(participantReceivables)
                .add(investments)
                .subtract(creditCardDebt)
                .subtract(otherLiabilities);
        return new LiquidNetWorthDto(date, cashAndBanks, receivables, participantReceivables,
                investments, creditCardDebt, otherLiabilities, liquidNetWorth);
    }
}
