package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.service.ResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminTools {

    private final ResetService resetService;

    @McpTool(
            name = "reset_account",
            description = """
                    ⚠️  DESTRUCTIVE — deletes ALL financial data for the current user account:
                    all transactions, loans, investments, debts, people, subscriptions,
                    expense groups, and accounts.

                    Default system accounts (Cash, Bank, Salary, etc.) are re-created automatically
                    after the reset so the account is ready to use from scratch.

                    The user record itself is NOT deleted — only their financial data.

                    You MUST pass confirm=true or the operation is rejected.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true)
    )
    public String resetAccount(
            @McpArg(name = "confirm", description = "Must be true to execute the reset", required = true) Boolean confirm
    ) {
        if (!Boolean.TRUE.equals(confirm)) {
            return "Reset cancelled. Pass confirm=true to proceed.";
        }
        resetService.resetUserData();
        return "Account reset complete. All financial data deleted and default accounts re-created. You can start fresh now.";
    }
}
