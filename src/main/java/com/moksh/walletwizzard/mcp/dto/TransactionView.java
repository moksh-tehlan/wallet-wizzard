package com.moksh.walletwizzard.mcp.dto;

import java.util.List;

public record TransactionView(
        String id,
        String date,
        String description,
        String entryType,
        List<LineView> lines
) {}
