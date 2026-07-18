package com.moksh.walletwizzard.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class GuideTools {

    @McpTool(
            name = "get_guide",
            description = """
                    Returns the complete usage guide for WalletWizzard tools.
                    Call this at the start of any session or whenever you are unsure how to
                    structure a request (account types, transaction patterns, error handling, etc.).
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getGuide() {
        return """
                # WalletWizzard — Agent Guide

                ## Creating Accounts

                Always call get_account_types before add_account if unsure what type/subType to use.
                Then create the account using type + subType. Do NOT pass parentId — the system
                places the account under the correct parent group automatically.

                Example: add_account(name="HDFC Bank", type="ASSET", subType="BANK_ACCOUNT")

                subType reference:
                  ASSET     + BANK_ACCOUNT     → Bank and savings accounts
                  ASSET     + CASH_AND_WALLET  → Physical cash, digital wallets, UPI balance
                  ASSET     + RECEIVABLE       → Money others owe you (informal lending)
                  ASSET     + OTHER_ASSET      → Investments, FDs, anything else you own
                  LIABILITY + CREDIT_CARD      → Credit cards
                  LIABILITY + LOAN_PAYABLE     → Formal loans you have taken
                  LIABILITY + OTHER_LIABILITY  → Any other money you owe
                  INCOME    + INCOME_ACCOUNT   → Sources of money coming in
                  EXPENSE   + EXPENSE_ACCOUNT  → Spending categories

                Debt and loan accounts are created automatically by add_debt / add_loan.
                Never create them manually.

                ## Querying Accounts

                list_accounts(subType="BANK_ACCOUNT")  → only bank accounts with balances
                list_accounts(type="EXPENSE")           → all expense accounts
                list_accounts()                         → everything

                ## Recording Transactions

                Every transaction must balance: total debits == total credits.

                Account type rules:
                  ASSET     → DEBIT increases, CREDIT decreases
                  LIABILITY → CREDIT increases, DEBIT decreases
                  INCOME    → CREDIT increases, DEBIT decreases
                  EXPENSE   → DEBIT increases, CREDIT decreases

                Common patterns:
                  Grocery ₹500 via debit card:  DEBIT Food&Dining 500,  CREDIT HDFC Bank 500
                  Salary received:              DEBIT HDFC Bank 50000,  CREDIT Salary 50000
                  Credit card purchase:         DEBIT Shopping 1000,    CREDIT Credit Card 1000
                  Pay credit card bill:         DEBIT Credit Card 5000, CREDIT HDFC Bank 5000
                  Transfer between accounts:    DEBIT Target Account X, CREDIT Source Account X

                entryType values:
                  EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT | LOAN_PAYMENT | SUBSCRIPTION |
                  OPENING_BALANCE | ADJUSTMENT

                ## Input Rules

                - Dates must be YYYY-MM-DD format.
                - Blank strings are treated the same as omitting the field.
                - Enum fields are case-insensitive (BANK_ACCOUNT and bank_account both work).
                - If you pass an invalid enum value the error lists all valid options.
                - Do not retry on error — read the message and fix the specific field.

                ## Error Examples

                  "Invalid date 'yesterday' for 'from'. Use YYYY-MM-DD format (e.g. 2026-07-15)."
                  "Invalid value 'WIRE' for 'entryType'. Valid values: EXPENSE | INCOME | ..."
                  "A transaction with referenceId <uuid> already exists."
                  "Journal entry is unbalanced: total debits = 500, total credits = 400."
                """;
    }
}
