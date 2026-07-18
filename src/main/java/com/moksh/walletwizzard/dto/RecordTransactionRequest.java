package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.EntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordTransactionRequest(
        @NotNull LocalDate date,

        @NotBlank String description,

        @NotNull EntryType entryType,

        /** Links this entry back to a subscription, loan, or debt record. Null for ad-hoc entries. */
        UUID referenceId,

        @NotNull @Size(min = 2, message = "A journal entry must have at least two lines.")
        List<@Valid LineRequest> lines
) {}
