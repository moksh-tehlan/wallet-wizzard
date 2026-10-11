package com.moksh.walletwizzard.reporting.dto;

import java.util.List;
import java.util.UUID;

public record MerchantCategorySuggestionDto(
        String merchant,
        List<Suggestion> suggestions
) {
    public record Suggestion(
            UUID accountId,
            String accountName,
            int useCount,
            double confidencePercent
    ) {}
}
