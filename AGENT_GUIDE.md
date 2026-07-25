# WalletWizzard — Agent Guide

This document describes how to use the WalletWizzard MCP tools correctly.
Read the relevant section before performing any operation.

---

## Tool Index

| Category           | Tools                                                                                                        |
|--------------------|--------------------------------------------------------------------------------------------------------------|
| Accounts           | `get_account_types`, `add_account`, `list_accounts`, `get_account_balance`                                   |
| Transactions       | `record_transaction`, `get_transactions`                                                                     |
| Reporting          | `get_cash_flow`, `get_net_worth`, `get_monthly_report`, `get_spending_by_category`, `get_spending_averages`, `get_monthly_trend` |
| Debts              | `add_debt`, `settle_debt`, `list_debts`, `get_debt_summary`                                                  |
| Loans              | `add_loan`, `record_loan_payment`, `list_loans`, `get_loan_summary`                                          |
| Loan Schedules     | `generate_loan_schedule`, `import_loan_schedule`, `get_loan_schedule`, `pay_installment`                     |
| Loan Participants  | `add_loan_participant`, `get_loan_share_summary`                                                             |
| Upcoming Payments  | `get_upcoming_loan_payments`, `get_upcoming_bills`                                                           |
| People             | `add_person`, `list_people`, `update_person`                                                                 |
| Person Balance     | `get_person_balance`                                                                                         |
| Subscriptions      | `add_subscription`, `record_subscription_payment`, `list_subscriptions`, `update_subscription_status`       |
| Expense Groups     | `create_expense_group`, `add_group_member`, `add_group_expense`, `list_groups`, `list_group_expenses`, `get_group_balance`, `settle_group_member`  |
| Guide              | `get_guide` (returns this guide as text)                                                                     |

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

Do NOT pass `parentId` — it is not a parameter on `add_account`. The subType handles all grouping automatically.

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
you never need to create them manually.

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
LOAN_PAYMENT | INSTALLMENT_PAYMENT | SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT`

---

## Debt Tracking

Use debts for **informal, unstructured** lending between people (e.g. "I lent Himanshu ₹5000").
For structured loans with EMI schedules, use Loans instead.

### Typical flow
```
1. add_person(name="Himanshu")                           → personId
2. add_debt(personId, amount, direction, bankAccountId)  → debtId
3. settle_debt(debtId, bankAccountId, amount)            → records (partial or full) repayment
4. get_debt_summary(debtId)                              → check remaining outstanding balance
```

`settle_debt` can be called multiple times for partial repayments. Balance = 0 means fully settled.

`direction`:
- `LENT` — you gave money to someone (creates a Receivable account for them)
- `BORROWED` — you received money from someone (creates a Payable account)

---

## Loans (Structured, with EMI)

Use loans for **formal, interest-bearing** financing (home loan, car loan, personal loan, etc.).

### Typical flow
```
1. add_loan(name, direction, principal, interestRate, startDate, bankAccountId)
   → loanId, loanAccountId
2. generate_loan_schedule(loanId, numberOfInstallments)
   → creates amortization schedule
3. get_loan_schedule(loanId)
   → see all installments with dueDate, principal, interest, status
4. pay_installment(installmentId, bankAccountId)
   → records journal entry, marks installment PAID
```

`direction`:
- `TAKEN` — you borrowed from a bank/person (creates Loan Payable liability)
- `GIVEN` — you lent money as a formal loan (creates Loan Receivable asset)

`interestRate` is a decimal: `0.0875` = 8.75% per annum.

### Installment statuses
| Status      | Meaning                              |
|-------------|--------------------------------------|
| `SCHEDULED` | Future installment, not yet due      |
| `DUE`       | Due date has arrived, not yet paid   |
| `PAID`      | Payment recorded                     |

The system automatically marks `SCHEDULED → DUE` every day at 00:05.

### Importing a custom schedule
If you have exact amounts from the bank (e.g. from a statement):
```
import_loan_schedule(loanId, installments=[
  {dueDate: "2026-02-01", principalAmount: "12000", interestAmount: "3000"},
  ...
])
```
Use this instead of `generate_loan_schedule`. Only one of the two can be called per loan.

---

## Shared Loans (Co-borrowers / Participants)

Add participants when multiple people share loan repayment responsibility.

### Flow
```
1. add_loan_participant(loanId, personId, sharePercent=50)
   → tracks that this person is responsible for 50% of each installment
2. get_loan_share_summary(loanId)
   → per-participant: totalShareAmount, paidByUser, currentlyDue, scheduledFuture
3. get_person_balance(personId)
   → see everything this person owes you across all debts + loan participations
```

`sharePercent` is 0–100. Multiple participants can be added; total can exceed 100% is allowed
(the user's own share is implicit — not tracked as a participant entry for themselves).

### Share accounting logic
- `paidByUser` = sum of PAID installment totals × sharePercent/100
  (you paid these and the participant should reimburse you)
- `currentlyDue` = sum of DUE installment totals × sharePercent/100
- `scheduledFuture` = sum of SCHEDULED installment totals × sharePercent/100

To actually receive repayment from a participant, record it as a debt settlement
(`add_debt` / `settle_debt`) when the person pays you back.

---

## Spending Baseline & Trends

```
get_spending_averages(months=6)
→ per expense category: averageMonthlySpend, maxMonthSpend, minMonthSpend, monthsWithData
   (only months with actual spend counted — doesn't dilute average with inactive months)

get_monthly_trend(months=6)
→ month-by-month income/expenses (most recent first) + averages across the period
   answers: "Am I spending more than usual?" / "What's my average savings rate?"
```

## Upcoming Payments

Two tools — use both for a complete view:

```
get_upcoming_loan_payments(days=30)    → loan installments due in 30 days
get_upcoming_bills(days=30)            → subscriptions due in 30 days
```

---

## Person Balance

Answers: "How much does Himanshu owe me?" / "What do I owe Riya?"

```
get_person_balance(personId)
→ {
    personName,
    directDebts: [...],          # from add_debt
    loanParticipations: [...],   # from add_loan_participant
    netBalance                   # positive = they owe you, negative = you owe them
  }
```

`netBalance` combines direct debt outstanding balances + loan participation shares
(paid + due portions for TAKEN loans; negative for GIVEN loans).

---

## Expense Groups (Splitwise-style)

Use expense groups to track shared spending with friends, flatmates, or travel companions.
You (the account owner) are always the implicit member — do not add yourself as a person.

### Typical flow
```
1. add_person(name="Himanshu")                          → personId (skip if already exists)
2. create_expense_group(name="Goa Trip",
       memberPersonIds=["<uuid>", "<uuid>"])             → groupId
3. add_group_expense(groupId, description="Hotel Night 1",
       totalAmount="6000",
       expenseAccountId="<uuid>",
       bankAccountId="<uuid>")
   → splits ₹6000 ÷ 4 people = ₹1500 each
   → journal entry posted: DEBIT Hotels, CREDIT HDFC Bank ₹6000
4. (repeat add_group_expense for each expense)
5. get_group_balance(groupId)
   → Himanshu owes you ₹3200, Riya owes you ₹2100, ...
6. settle_group_member(groupId, personId, bankAccountId)
   → records settlement journal entry, marks splits settled
```

### If someone else paid
```
add_group_expense(groupId, description="Cab", totalAmount="800",
    paidByPersonId="<himanshu-uuid>")
→ tracks your equal share (₹200) as what you owe Himanshu
→ no journal entry posted (you didn't pay from your bank)
```

### Balance fields
| Field        | Meaning                                              |
|--------------|------------------------------------------------------|
| `theyOweYou` | Sum of their unsettled shares from expenses you paid |
| `youOweThem` | Sum of your unsettled share from expenses they paid  |
| `net`        | positive = they owe you, negative = you owe them     |

Settlement journal entries:
- Receiving money from a member → DEBIT bank, CREDIT "Group Reimbursements" (income)
- Paying money to a member → DEBIT "Group Reimbursements", CREDIT bank

---

## Subscriptions

```
add_subscription(name, amount, billingCycle, paymentAccountId, expenseAccountId, nextBillingDate)
record_subscription_payment(subscriptionId, paymentDate)   → records expense, advances nextBillingDate
list_subscriptions(status="ACTIVE")
update_subscription_status(subscriptionId, status="CANCELLED")
get_upcoming_bills(days=30)
```

`billingCycle`: `WEEKLY | MONTHLY | QUARTERLY | YEARLY`

---

## Input Rules

- All date fields expect **YYYY-MM-DD** format.
- All UUID fields expect standard UUID format.
- Blank strings (`""`) are treated the same as omitting the field (null).
- Enum fields are case-insensitive — `"bank_account"` and `"BANK_ACCOUNT"` both work.
- If you pass an invalid enum value the error message lists all valid options.

---

## Error Messages

Errors are descriptive and actionable. Examples:

- `Invalid date 'yesterday' for 'from'. Use YYYY-MM-DD format.`
- `Invalid value 'WIRE' for 'entryType'. Valid values: EXPENSE | INCOME | ...`
- `Invalid UUID 'abc123' for 'accountId'.`
- `A transaction with referenceId <uuid> already exists.`
- `Journal entry is unbalanced: total debits = 500, total credits = 400.`
- `Loan 'Home Loan' already has a schedule. Use get_loan_schedule to view it.`
- `Installment #3 is already paid.`
- `Himanshu is already a participant in loan 'Home Loan'.`

Read the error and fix the specific field — do not retry blindly.
