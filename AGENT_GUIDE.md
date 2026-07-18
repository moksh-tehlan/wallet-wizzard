# WalletWizzard — Agent Guide

This document describes how to use the WalletWizzard MCP tools correctly.
Read the relevant section before performing any operation.

---

## Creating Accounts

**Always call `get_account_types` before `add_account`** if you are unsure what
type/subType to use. It returns all valid combinations with descriptions.

```
get_account_types()
→ [{ type, subType, description }, ...]
```

Then create the account using `type` + `subType`. Do NOT pass `parentId` — the
system places the account under the correct parent group automatically based on
`subType`.

```
add_account(name="HDFC Bank", type="ASSET", subType="BANK_ACCOUNT")
```

### subType reference

| type      | subType          | use for                                      |
|-----------|------------------|----------------------------------------------|
| ASSET     | BANK_ACCOUNT     | Bank and savings accounts                    |
| ASSET     | CASH_AND_WALLET  | Physical cash, digital wallets, UPI balance  |
| ASSET     | RECEIVABLE       | Money others owe you (informal lending)      |
| ASSET     | OTHER_ASSET      | Investments, FDs, anything else you own      |
| LIABILITY | CREDIT_CARD      | Credit cards                                 |
| LIABILITY | LOAN_PAYABLE     | Formal loans you have taken                  |
| LIABILITY | OTHER_LIABILITY  | Any other money you owe                      |
| INCOME    | INCOME_ACCOUNT   | Sources of money coming in                   |
| EXPENSE   | EXPENSE_ACCOUNT  | Spending categories                          |

### Auto-created accounts
Debt and loan accounts are created automatically by `add_debt` / `add_loan` —
you never need to create them manually. They are placed under the correct
parent group automatically.

---

## Querying Accounts

`list_accounts` accepts two optional filters. `subType` is more precise and
takes precedence over `type` if both are given.

```
list_accounts(subType="BANK_ACCOUNT")   → only bank accounts
list_accounts(type="EXPENSE")           → all expense accounts
list_accounts()                         → everything
```

---

## Recording Transactions

Double-entry rules — every transaction must balance (debits = credits):

| Account type | DEBIT means  | CREDIT means |
|--------------|--------------|--------------|
| ASSET        | increases    | decreases    |
| LIABILITY    | decreases    | increases    |
| INCOME       | decreases    | increases    |
| EXPENSE      | increases    | decreases    |

Common patterns:
- Grocery ₹500 via debit card: DEBIT Food&Dining 500, CREDIT HDFC Bank 500
- Salary received: DEBIT HDFC Bank 50000, CREDIT Salary 50000
- Credit card purchase: DEBIT Shopping 1000, CREDIT Credit Card 1000
- Pay credit card bill: DEBIT Credit Card 5000, CREDIT HDFC Bank 5000

`entryType` values: `EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT |
LOAN_PAYMENT | SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT`

---

## Input Rules

- All date fields expect **YYYY-MM-DD** format. Passing an invalid date returns
  a clear error — do not guess formats.
- All UUID fields expect standard UUID format. Passing garbage returns a clear
  error message with the field name.
- Blank strings (`""`) are treated the same as omitting the field (null).
- Enum fields (type, subType, direction, etc.) are case-insensitive —
  `"bank_account"` and `"BANK_ACCOUNT"` both work.
- If you pass an invalid enum value the error message lists all valid options.

---

## Error Messages

Errors are descriptive and actionable. Examples:

- `Invalid date 'yesterday' for 'from'. Use YYYY-MM-DD format (e.g. 2026-07-15).`
- `Invalid value 'WIRE' for 'entryType'. Valid values: EXPENSE | INCOME | TRANSFER | ...`
- `Invalid UUID 'abc123' for 'accountId'.`
- `A transaction with referenceId <uuid> already exists.`
- `Journal entry is unbalanced: total debits = 500, total credits = 400. They must be equal.`

Read the error and fix the specific field — do not retry blindly.
