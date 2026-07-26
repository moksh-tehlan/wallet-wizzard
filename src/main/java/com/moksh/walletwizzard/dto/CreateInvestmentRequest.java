package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.InvestmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvestmentRequest(
        @NotBlank String name,
        @NotNull InvestmentType type,

        /** Amount invested / current balance (cost basis). */
        @NotNull @DecimalMin("0.01") BigDecimal amount,

        // ── MF specific ──────────────────────────────────────────────────────
        /** mfapi.in scheme code (required for MUTUAL_FUND). */
        String schemeCode,
        /** Units purchased (required for MUTUAL_FUND). */
        BigDecimal units,

        // ── EPF / FD / RD ────────────────────────────────────────────────────
        /** Annual interest rate as decimal: 0.0825 = 8.25% (defaults to 0.0825 for EPF). */
        BigDecimal interestRate,
        /** Monthly contribution amount (required for EPF and RD). */
        BigDecimal monthlyContribution,

        // ── Common ───────────────────────────────────────────────────────────
        LocalDate startDate,
        /** Maturity date (required for FD and RD). */
        LocalDate maturityDate,

        /**
         * Bank account to debit for the initial investment.
         * Null for EPF (existing payroll balance — recorded as opening balance).
         */
        UUID bankAccountId,

        String notes
) {}
