package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.BillingCycle;
import com.moksh.walletwizzard.enums.RecurringSide;
import com.moksh.walletwizzard.enums.ScheduleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotBlank String name,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull BillingCycle billingCycle,
        LocalDate nextBillingDate,

        /** DEBIT = outgoing (bill/expense), CREDIT = incoming (salary/rent received). */
        @NotNull RecurringSide side,

        /** How to compute the actual due date within each billing period. */
        @NotNull ScheduleType scheduleType,

        /** Bank account or credit card involved (pays out for DEBIT, receives for CREDIT). */
        @NotNull UUID paymentAccountId,

        /** Expense account for DEBIT, income account for CREDIT. */
        @NotNull UUID categoryAccountId,

        String notes
) {}
