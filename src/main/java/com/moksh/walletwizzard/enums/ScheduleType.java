package com.moksh.walletwizzard.enums;

import java.time.LocalDate;

public enum ScheduleType {
    /** Same calendar day each period (e.g. always the 5th). */
    FIXED_DAY,
    /** Last calendar day of the period's month. */
    LAST_DAY,
    /** Last Monday–Friday of the period's month (salary last working day). */
    LAST_WEEKDAY;

    public LocalDate applyTo(LocalDate advancedDate) {
        return switch (this) {
            case FIXED_DAY -> advancedDate;
            case LAST_DAY -> advancedDate.withDayOfMonth(advancedDate.lengthOfMonth());
            case LAST_WEEKDAY -> {
                LocalDate lastDay = advancedDate.withDayOfMonth(advancedDate.lengthOfMonth());
                yield switch (lastDay.getDayOfWeek()) {
                    case SATURDAY -> lastDay.minusDays(1);
                    case SUNDAY   -> lastDay.minusDays(2);
                    default       -> lastDay;
                };
            }
        };
    }
}
