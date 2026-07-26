package com.moksh.walletwizzard.mcp.dto;

public record SubView(
        String id,
        String name,
        String amount,
        String billingCycle,
        String side,
        String scheduleType,
        String nextBillingDate
) {}
