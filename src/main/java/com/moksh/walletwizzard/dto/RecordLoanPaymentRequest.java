package com.moksh.walletwizzard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordLoanPaymentRequest(
        @NotNull UUID loanId,

        @NotNull @DecimalMin("0.01") BigDecimal principalPortion,

        /** Zero for interest-free or fully-interest payments. */
        @NotNull @DecimalMin("0.00") BigDecimal interestPortion,

        @NotNull LocalDate date,

        /** Bank/card account through which the payment was made or received. */
        @NotNull UUID bankAccountId
) {
    public BigDecimal totalAmount() {
        return principalPortion.add(interestPortion);
    }
}
