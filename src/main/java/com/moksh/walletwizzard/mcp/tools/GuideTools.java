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

                ## Tool Index
                  Accounts:          get_account_types, add_account, list_accounts, get_account_balance
                  Transactions:      record_transaction, get_transactions
                  Reporting:         get_cash_flow, get_net_worth, get_monthly_report, get_spending_by_category, get_spending_averages, get_monthly_trend
                  Debts:             add_debt, list_debts, get_debt_summary, settle_debt
                  Loans:             add_loan, record_loan_payment, list_loans, get_loan_summary
                  Loan Schedules:    generate_loan_schedule, import_loan_schedule, get_loan_schedule, pay_installment
                  Loan Participants: add_loan_participant, get_loan_share_summary
                  Upcoming:          get_upcoming_loan_payments, get_upcoming_bills
                  People:            add_person, list_people, update_person
                  Person Balance:    get_person_balance
                  Recurring:         add_recurring_transaction, record_recurring_payment, list_recurring_transactions, update_recurring_transaction_status, get_upcoming_bills
                  Expense Groups:    create_expense_group, add_group_member, add_group_expense, list_groups, list_group_expenses, get_group_balance, settle_group_member

                ## Accounts

                Always call get_account_types first. Use type + subType; never pass parentId.
                  add_account(name="HDFC Bank", type="ASSET", subType="BANK_ACCOUNT")

                subType routing:
                  ASSET + BANK_ACCOUNT    → Bank/savings accounts
                  ASSET + CASH_AND_WALLET → Cash, wallets, UPI
                  ASSET + RECEIVABLE      → Informal money owed to you
                  ASSET + OTHER_ASSET     → Investments, FDs
                  LIABILITY + CREDIT_CARD    → Credit cards
                  LIABILITY + LOAN_PAYABLE   → Formal loans taken
                  LIABILITY + OTHER_LIABILITY→ Any other debt owed
                  INCOME + INCOME_ACCOUNT    → Income categories
                  EXPENSE + EXPENSE_ACCOUNT  → Spending categories

                Debt and loan accounts are auto-created. Never create them manually.

                ## Transactions

                Every entry must balance: total DEBIT == total CREDIT.
                  ASSET:     DEBIT increases, CREDIT decreases
                  LIABILITY: CREDIT increases, DEBIT decreases
                  INCOME:    CREDIT increases, DEBIT decreases
                  EXPENSE:   DEBIT increases, CREDIT decreases

                entryType: EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT | LOAN_PAYMENT |
                           INSTALLMENT_PAYMENT | SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT

                ## Debts (informal, no EMI schedule)

                Flow:
                  1. add_person(name="Himanshu") → personId
                  2. add_debt(personId, amount, direction=LENT, bankAccountId) → debtId
                  3. settle_debt(debtId, bankAccountId, amount) when repaid
                  4. get_person_balance(personId) for full picture

                direction: LENT (you gave money) | BORROWED (you received money)

                ## Loans (formal, with EMI schedule)

                Flow:
                  1. add_loan(name, direction, principal, interestRate, startDate, bankAccountId) → loanId
                  2. generate_loan_schedule(loanId, numberOfInstallments) → creates amortization schedule
                     OR import_loan_schedule(loanId, [{dueDate, principalAmount, interestAmount}])
                  3. get_loan_schedule(loanId) → see all installments
                  4. pay_installment(installmentId, bankAccountId) → marks PAID, posts journal entry

                direction: TAKEN (you borrowed) | GIVEN (you lent formally)
                interestRate: decimal — 0.0875 = 8.75% per annum

                Installment statuses: SCHEDULED → DUE (auto, daily) → PAID

                ## Shared Loans (co-borrowers)

                Flow:
                  1. add_loan_participant(loanId, personId, sharePercent=50)
                  2. get_loan_share_summary(loanId)
                     → per participant: paidByUser, currentlyDue, scheduledFuture
                  3. get_person_balance(personId)
                     → netBalance: positive = they owe you, negative = you owe them

                paidByUser = PAID installment totals × share%. This is what they owe you.
                To receive repayment, settle it via add_debt / settle_debt.

                ## Recurring Transactions

                Handles both outgoing bills (Netflix, rent, EMI) and incoming income (salary, dividend, rental income).

                Flow:
                  1. add_recurring_transaction(name, amount, billingCycle, side, scheduleType, paymentAccountId, categoryAccountId)
                  2. record_recurring_payment(recurringId) each cycle → posts journal entry, advances next date
                  3. list_recurring_transactions(status=ACTIVE) to see all
                  4. update_recurring_transaction_status(recurringId, status=PAUSED/CANCELLED) to pause or cancel

                side:
                  DEBIT  → money goes out (bills, subscriptions) — DR categoryAccount (expense), CR paymentAccount
                  CREDIT → money comes in (salary, rent received) — DR paymentAccount, CR categoryAccount (income)

                scheduleType:
                  FIXED_DAY    → same calendar day each cycle (default)
                  LAST_DAY     → last calendar day of the month
                  LAST_WEEKDAY → last Monday–Friday of the month (e.g. salary paid last working day)

                ## Upcoming Payments

                  get_upcoming_loan_payments(days=30) → DUE + SCHEDULED installments
                  get_upcoming_bills(days=30)          → recurring transaction due dates (both DEBIT and CREDIT)

                ## Input Rules

                - Dates: YYYY-MM-DD only.
                - Blank strings ("") equal null (field omitted).
                - Enum fields are case-insensitive.
                - Invalid enum → error lists all valid values.
                - Do not retry blindly — read the error and fix the named field.

                ## Common Errors

                  "Invalid date 'yesterday' for 'from'. Use YYYY-MM-DD format."
                  "Invalid value 'WIRE' for 'entryType'. Valid values: EXPENSE | INCOME | ..."
                  "Journal entry is unbalanced: total debits = 500, total credits = 400."
                  "Loan 'Home Loan' already has a schedule. Use get_loan_schedule to view it."
                  "Installment #3 is already paid."
                  "Himanshu is already a participant in loan 'Home Loan'."
                  "Himanshu is already a member of group 'Goa Trip'."
                  "Group 'Goa Trip' has no members. Add members before adding expenses."

                ## Expense Groups (Splitwise-style)

                For tracking shared expenses with friends/flatmates.
                You are always the implicit owner — do not add yourself as a member.

                Flow:
                  1. add_person for each friend if not already in the system
                  2. create_expense_group(name, memberPersonIds=[...]) → groupId
                  3. add_group_expense(groupId, description, totalAmount, expenseAccountId, bankAccountId) each time
                     → auto-splits equally: each member's share = totalAmount ÷ (members + 1)
                     → journal entry posted automatically when you pay (DEBIT expense, CREDIT bank)
                  4. get_group_balance(groupId) → real-time balance per member
                     theyOweYou, youOweThem, net (positive = they owe you)
                  5. settle_group_member(groupId, personId, bankAccountId) when someone pays up
                     → posts journal entry, marks their splits as settled

                If someone else paid (not you): pass paidByPersonId, omit expenseAccountId + bankAccountId.
                Your share is tracked as what you owe that person.

                ## Spending Baseline & Trends

                  get_spending_averages(months=6)
                    → per category: averageMonthlySpend, maxMonthSpend, minMonthSpend, monthsWithData
                  get_monthly_trend(months=6)
                    → month-by-month income/expenses + averages; most recent first
                """;
    }
}
