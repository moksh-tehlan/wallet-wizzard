package com.moksh.walletwizzard.enums;

import java.time.LocalDate;

public enum BillingCycle {
    WEEKLY, MONTHLY, QUARTERLY, YEARLY;

    public LocalDate advance(LocalDate from) {
        return switch (this) {
            case WEEKLY    -> from.plusWeeks(1);
            case MONTHLY   -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY    -> from.plusYears(1);
        };
    }
}
