package com.moksh.walletwizzard.mcp.dto;

public record LineView(
        String accountId,
        String accountName,
        String amount,
        String side,
        String memo
) {}
