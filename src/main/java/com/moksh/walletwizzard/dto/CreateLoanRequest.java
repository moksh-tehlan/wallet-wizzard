package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.LoanDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLoanRequest(
        @NotBlank String name,
        @NotNull LoanDirection direction,

        @NotNull @DecimalMin("0.01") BigDecimal principal,

        /** 0.0875 = 8.75%. Null means zero-interest loan. */
        BigDecimal interestRate,

        BigDecimal emiAmount,
        LocalDate startDate,
        LocalDate endDate,

        /**
         * Account the loan proceeds were deposited into (TAKEN)
         * or drawn from (GIVEN). The loan payable/receivable account
         * is created automatically.
         */
        @NotNull UUID bankAccountId,

        /**
         * Expense account for interest (TAKEN) or income account (GIVEN).
         * Null → service picks the first matching system account.
         */
        UUID interestAccountId,

        String notes
) {}
