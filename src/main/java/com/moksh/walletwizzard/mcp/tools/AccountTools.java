package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.CreateAccountRequest;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.reporting.ReportingService;
import com.moksh.walletwizzard.reporting.dto.AccountBalanceDto;
import com.moksh.walletwizzard.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountTools {

    private final AccountService accountService;
    private final ReportingService reportingService;

    @McpTool(
            name = "add_account",
            description = """
                    Creates a new financial account in the chart of accounts.

                    type values: ASSET | LIABILITY | INCOME | EXPENSE | EQUITY

                    subType automatically places the account under the correct parent group —
                    you do NOT need to pass parentId for standard accounts:
                      BANK_ACCOUNT    → grouped under "Bank Accounts"   (use for all bank/savings accounts)
                      CASH_AND_WALLET → grouped under "Cash & Equivalents" (use for physical cash, wallets)
                      RECEIVABLE      → grouped under "Receivables"     (use for money owed to you)
                      CREDIT_CARD     → grouped under "Credit Cards"    (use for credit cards)
                      LOAN_PAYABLE    → grouped under "Loans"           (use for loans you have taken)
                      OTHER_ASSET     → no parent group
                      OTHER_LIABILITY → no parent group
                      INCOME_ACCOUNT  → no parent group (flat income list)
                      EXPENSE_ACCOUNT → no parent group (flat expense list)

                    Always provide subType for correct grouping. Omit it only for EQUITY accounts.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addAccount(
            @McpArg(name = "name", description = "Account name e.g. 'HDFC Savings', 'Food & Dining'", required = true)
            String name,
            @McpArg(name = "type", description = "ASSET | LIABILITY | INCOME | EXPENSE | EQUITY", required = true)
            String type,
            @McpArg(name = "subType", description = "BANK_ACCOUNT | CASH_AND_WALLET | RECEIVABLE | CREDIT_CARD | LOAN_PAYABLE | OTHER_ASSET | OTHER_LIABILITY | INCOME_ACCOUNT | EXPENSE_ACCOUNT", required = false)
            String subType,
            @McpArg(name = "notes", description = "Optional notes about this account", required = false)
            String notes
    ) {
        var request = new CreateAccountRequest(
                name,
                McpInputs.requireEnum(type, "type", AccountType.class),
                McpInputs.parseEnum(subType, "subType", AccountSubType.class),
                null,
                notes
        );
        var account = accountService.createAccount(request);
        return "Account created: id=" + account.getId() + ", name=" + account.getName()
                + ", type=" + account.getType() + ", normalBalance=" + account.getNormalBalance();
    }

    @McpTool(
            name = "list_accounts",
            description = """
                    Lists all active accounts with their current balances.
                    Filter by type (broad) or subType (precise). subType takes precedence if both given.

                    type:    ASSET | LIABILITY | INCOME | EXPENSE | EQUITY
                    subType: BANK_ACCOUNT | CASH_AND_WALLET | RECEIVABLE | CREDIT_CARD |
                             LOAN_PAYABLE | OTHER_ASSET | OTHER_LIABILITY |
                             INCOME_ACCOUNT | EXPENSE_ACCOUNT
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<AccountBalanceDto> listAccounts(
            @McpArg(name = "type", description = "Broad filter: ASSET | LIABILITY | INCOME | EXPENSE | EQUITY (omit for all)", required = false)
            String type,
            @McpArg(name = "subType", description = "Precise filter: BANK_ACCOUNT | CASH_AND_WALLET | RECEIVABLE | CREDIT_CARD | LOAN_PAYABLE | ... (omit for all)", required = false)
            String subType
    ) {
        return reportingService.getAccountBalances(
                McpInputs.parseEnum(type, "type", AccountType.class),
                McpInputs.parseEnum(subType, "subType", AccountSubType.class)
        );
    }

    @McpTool(
            name = "get_account_balance",
            description = """
                    Returns the current balance of a specific account.
                    Optionally pass asOfDate (YYYY-MM-DD) for a historical snapshot.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getAccountBalance(
            @McpArg(name = "accountId", description = "UUID of the account", required = true)
            String accountId,
            @McpArg(name = "asOfDate", description = "ISO date YYYY-MM-DD for historical balance (omit for today)", required = false)
            String asOfDate
    ) {
        UUID id = McpInputs.requireUuid(accountId, "accountId");
        var parsedDate = McpInputs.parseDate(asOfDate, "asOfDate");
        var balance = parsedDate != null
                ? accountService.getBalanceAsOf(id, parsedDate)
                : accountService.getBalance(id);
        var account = accountService.getById(id);
        return "Account: " + account.getName() + " | Balance: " + balance + " | Type: " + account.getType();
    }

    @McpTool(
            name = "get_account_types",
            description = """
                    Returns all valid type + subType combinations to use when creating an account.
                    Call this before add_account if you are unsure which type/subType to pick.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<Map<String, String>> getAccountTypes() {
        return List.of(
                entry("ASSET",     "BANK_ACCOUNT",    "Bank accounts, savings accounts (HDFC, SBI, PNB...)"),
                entry("ASSET",     "CASH_AND_WALLET", "Physical cash, digital wallets, UPI balance"),
                entry("ASSET",     "RECEIVABLE",      "Money others owe you (informal lending)"),
                entry("ASSET",     "OTHER_ASSET",     "Investments, fixed deposits, anything else you own"),
                entry("LIABILITY", "CREDIT_CARD",     "Credit cards"),
                entry("LIABILITY", "LOAN_PAYABLE",    "Formal loans you have taken (home loan, car loan...)"),
                entry("LIABILITY", "OTHER_LIABILITY", "Any other money you owe"),
                entry("INCOME",    "INCOME_ACCOUNT",  "Sources of money coming in (salary, freelance, interest)"),
                entry("EXPENSE",   "EXPENSE_ACCOUNT", "Spending categories (food, rent, transport...)")
        );
    }

    private Map<String, String> entry(String type, String subType, String description) {
        return Map.of("type", type, "subType", subType, "description", description);
    }

    @McpTool(
            name = "rename_account",
            description = "Renames an account. Cannot change type or normal balance after creation.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    public String renameAccount(
            @McpArg(name = "accountId", description = "UUID of the account", required = true) String accountId,
            @McpArg(name = "newName", description = "New display name for the account", required = true) String newName
    ) {
        var account = accountService.rename(McpInputs.requireUuid(accountId, "accountId"), newName);
        return "Account renamed to: " + account.getName();
    }
}
