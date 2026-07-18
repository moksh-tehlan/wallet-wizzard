package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.BillingCycle;
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

        /** Bank or credit card account charged for this subscription. */
        @NotNull UUID paymentAccountId,

        /** Expense category (e.g. Subscriptions, Entertainment). */
        @NotNull UUID expenseAccountId,

        String notes
) {}
