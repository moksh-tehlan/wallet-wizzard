package com.moksh.walletwizzard.mcp.tools;

/**
 * One leg of a double-entry journal entry as passed by Claude.
 * Uses String fields so the MCP JSON schema is unambiguous.
 */
public record LineInput(
        String accountId,  // UUID string
        String amount,     // decimal string e.g. "500.00"
        String side,       // "DEBIT" or "CREDIT"
        String memo        // optional per-line note
) {}
