package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.EntryType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Query parameters for listing journal entries.
 * All fields are optional — null means "no filter on that dimension".
 */
public record TransactionFilter(
        UUID accountId,
        LocalDate from,
        LocalDate to,
        EntryType entryType,
        int limit
) {
    public TransactionFilter {
        if (limit <= 0 || limit > 500) limit = 50;
    }

    public static TransactionFilter defaultFilter() {
        return new TransactionFilter(null, null, null, null, 50);
    }
}
