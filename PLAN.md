# WalletWizzard — Implementation Plan

Personal finance system built as an MCP server. Multi-user, production-grade from day one.

---

## What We're Building

A Spring Boot MCP server that lets users manage their complete financial life through Claude:
track income and expenses, manage multiple bank accounts and credit cards, record debts and
lendings with specific people, track loans with EMI schedules, and manage recurring
subscriptions — all grounded in proper double-entry accounting.

---

## Current Project State

```
Spring Boot  4.1.0
Java         26
Build        Gradle
Group        com.moksh
Spring AI    2.0.0 (MCP Server included)

Already in build.gradle:
  ✅ spring-boot-starter-data-jpa
  ✅ spring-boot-starter-security
  ✅ spring-boot-starter-security-oauth2-resource-server
  ✅ spring-boot-starter-validation
  ✅ spring-ai-starter-mcp-server
  ✅ postgresql (runtime)
  ✅ lombok

Still needed:
  ☐ org.flywaydb:flyway-core
  ☐ org.flywaydb:flyway-database-postgresql
```

---

## Architecture Decisions (Finalized)

### Accounting Model: Double-Entry Bookkeeping

Every financial event has exactly two sides — debiting one account and crediting another.
`SUM(DEBIT lines) = SUM(CREDIT lines)` is enforced on every journal entry.

Account balance is always **computed** from journal lines — no stored balance field anywhere.
This eliminates drift and makes the system correct by construction.

```
Account Types            Normal Balance   Examples
─────────────────────────────────────────────────────────────
ASSET                    DEBIT            Bank accounts, cash, receivables (money lent)
LIABILITY                CREDIT           Credit cards, loans, payables (money borrowed)
INCOME                   CREDIT           Salary, freelance, interest earned
EXPENSE                  DEBIT            Food, rent, subscriptions, utilities
EQUITY                   CREDIT           Opening balances, net worth
```

How common scenarios map to journal entries:

```
Scenario                    DEBIT                          CREDIT
──────────────────────────────────────────────────────────────────────────
Salary received             Bank Account (ASSET)           Salary (INCOME)
Grocery by debit card       Food & Dining (EXPENSE)        Bank Account (ASSET)
Credit card purchase        Shopping (EXPENSE)             Credit Card (LIABILITY)
Pay credit card bill        Credit Card (LIABILITY)        Bank Account (ASSET)
Transfer savings→checking   Checking (ASSET)               Savings (ASSET)
Lend ₹500 to John           Receivable-John (ASSET)        Bank Account (ASSET)
Borrow ₹500 from Jane       Bank Account (ASSET)           Payable-Jane (LIABILITY)
John repays                 Bank Account (ASSET)           Receivable-John (ASSET)
EMI payment                 Loan Payable (LIAB) +          Bank Account (ASSET)
                            Interest Expense (EXPENSE)
Subscription charged        Subscriptions (EXPENSE)        Credit Card (LIABILITY)
```

Debts and lendings are not a separate concept — they are accounts in the chart:
- Money lent → creates a Receivable (ASSET) account. Balance > 0 = still owed.
- Money borrowed → creates a Payable (LIABILITY) account. Balance > 0 = still owed.
- Balance = 0 means settled. No status field needed.

### Primary Keys: UUID v7

```java
@Id
@UuidGenerator(style = UuidGenerator.Style.TIME) // Hibernate 6.4+ built-in
private UUID id;
```

UUID v7 is time-ordered (first 48 bits = millisecond timestamp) so B-tree index locality
is nearly as good as sequential integers, while being globally unique without a coordinator.

### Timestamps

```
DB type       Java type     Used for
──────────────────────────────────────────────────
TIMESTAMPTZ   Instant       created_at, updated_at
DATE          LocalDate     transaction date (no time needed)
```

Never store epoch as BIGINT — Postgres native date functions won't work on it.

### Database: PostgreSQL Only

No ClickHouse. At thousands of users with ~10k transactions each, PostgreSQL handles
100M rows trivially. ClickHouse would add a second DB, a sync pipeline, and break ACID.

Analytics strategy inside PostgreSQL:
- Composite indexes on `(user_id, date)` and `(user_id, account_id)`
- Materialized views for expensive monthly/yearly aggregations
- Table partitioning by date if scale reaches 500M+ rows

### Multi-Tenancy: PostgreSQL Row Level Security (RLS)

Shared schema. Every table has `user_id`. RLS enforces isolation at the database level —
even a bug in application code cannot leak one user's data to another.

```sql
-- Policy on every table (example for accounts):
CREATE POLICY user_isolation ON accounts
    USING (user_id = current_setting('app.current_user_id')::BIGINT);

-- Hibernate interceptor sets this before every query:
-- SET LOCAL app.current_user_id = '42'
```

Auth: Spring's built-in OAuth2 Resource Server (JWT). The user ID is extracted from the
JWT claim and stored in a ThreadLocal TenantContext, which the Hibernate interceptor reads.

### Hibernate / JPA Rules

```
Rule                              Why
──────────────────────────────────────────────────────────────────────────
All @OneToMany → LAZY             Prevent accidental full-graph loads
Reads → DTO projections only      Never load managed entities for reports
@Transactional(readOnly=true)     On every read service method
@Version on mutable entities      Optimistic locking, prevents lost updates
hibernate.jdbc.batch_size=50      Batch inserts/updates
reWriteBatchedInserts=true        PostgreSQL JDBC optimization
HikariCP max pool = 2×cores + 1  Connection pool sizing formula
@SequenceGenerator allocationSize=50  NEVER GenerationType.IDENTITY (kills batching)
Flyway for all migrations         Never hbm2ddl.auto=update in production
```

---

## Database Schema

### Core Tables

```sql
-- users
id UUID (v7), email, name, currency CHAR(3) DEFAULT 'INR', created_at TIMESTAMPTZ

-- accounts (chart of accounts, hierarchical)
id UUID (v7), user_id, name, type (ASSET|LIABILITY|INCOME|EXPENSE|EQUITY),
normal_balance (DEBIT|CREDIT), parent_id (self-ref), is_system BOOLEAN,
is_active BOOLEAN, notes TEXT

-- tags (system defaults + user-created)
id UUID (v7), user_id, name, is_system BOOLEAN

-- journal_entries (immutable event header)
id UUID (v7), user_id, date DATE, description TEXT,
entry_type (EXPENSE|INCOME|TRANSFER|DEBT_SETTLEMENT|LOAN_PAYMENT|SUBSCRIPTION),
reference_id UUID, created_at TIMESTAMPTZ

-- journal_entry_lines (the double-entry legs)
id UUID (v7), journal_entry_id, account_id, amount NUMERIC(18,2) CHECK > 0,
side (DEBIT|CREDIT), memo TEXT

-- journal_entry_tags (M2M)
journal_entry_id, tag_id

-- people (contacts for debts/loans)
id UUID (v7), user_id, name, phone, email, notes

-- debt_records (metadata only; truth is in account balance)
id UUID (v7), user_id, person_id, account_id (the receivable/payable),
direction (LENT|BORROWED), original_amount, description, due_date DATE

-- loans
id UUID (v7), user_id, account_id, name, direction (TAKEN|GIVEN),
principal, interest_rate NUMERIC(6,4), emi_amount, start_date DATE, end_date DATE, notes

-- subscriptions
id UUID (v7), user_id, name, amount, billing_cycle (WEEKLY|MONTHLY|QUARTERLY|YEARLY),
next_billing_date DATE, payment_account_id, expense_account_id,
status (ACTIVE|PAUSED|CANCELLED)
```

### Indexes

```sql
-- On every table: individual index on user_id
-- On journal_entries:
CREATE INDEX idx_je_user_date ON journal_entries (user_id, date DESC);
-- On journal_entry_lines:
CREATE INDEX idx_jel_account ON journal_entry_lines (account_id);
CREATE INDEX idx_jel_entry   ON journal_entry_lines (journal_entry_id);
-- On accounts:
CREATE INDEX idx_acc_user_type ON accounts (user_id, type);
-- On subscriptions:
CREATE INDEX idx_sub_next_bill ON subscriptions (user_id, next_billing_date);
```

### Predefined System Accounts (seeded per new user)

```
ASSET
  Cash
  Bank Account — Checking
  Bank Account — Savings

LIABILITY
  Credit Card

INCOME
  Salary
  Freelance Income
  Other Income

EXPENSE
  Food & Dining
  Transportation
  Housing & Rent
  Utilities
  Entertainment
  Healthcare
  Shopping
  Subscriptions
  Education
  Other Expense

EQUITY
  Opening Balance Equity
```

---

## Package Structure

```
src/main/java/com/moksh/walletwizzard/
├── config/
│   ├── HibernateConfig.java          # HikariCP + batch settings
│   ├── SecurityConfig.java           # OAuth2 resource server config
│   ├── TenantContext.java            # ThreadLocal<UUID> userId holder
│   ├── RlsInterceptor.java           # Hibernate interceptor: SET LOCAL app.current_user_id
│   └── McpServerConfig.java          # Spring AI MCP server bean wiring
│
├── domain/
│   ├── user/
│   │   ├── User.java
│   │   ├── UserRepository.java
│   │   └── UserService.java
│   │
│   ├── account/
│   │   ├── Account.java
│   │   ├── AccountType.java          # enum: ASSET, LIABILITY, INCOME, EXPENSE, EQUITY
│   │   ├── NormalBalance.java        # enum: DEBIT, CREDIT
│   │   ├── AccountRepository.java
│   │   └── AccountService.java
│   │
│   ├── journal/
│   │   ├── JournalEntry.java
│   │   ├── JournalEntryLine.java
│   │   ├── EntrySide.java            # enum: DEBIT, CREDIT
│   │   ├── EntryType.java            # enum: EXPENSE, INCOME, TRANSFER, ...
│   │   ├── JournalEntryRepository.java
│   │   └── AccountingService.java    # enforces SUM(DEBIT) == SUM(CREDIT)
│   │
│   ├── people/
│   │   ├── Person.java
│   │   ├── PersonRepository.java
│   │   └── PersonService.java
│   │
│   ├── debt/
│   │   ├── DebtRecord.java
│   │   ├── DebtDirection.java        # enum: LENT, BORROWED
│   │   ├── DebtRecordRepository.java
│   │   └── DebtService.java
│   │
│   ├── loan/
│   │   ├── Loan.java
│   │   ├── LoanDirection.java        # enum: TAKEN, GIVEN
│   │   ├── LoanRepository.java
│   │   └── LoanService.java
│   │
│   └── subscription/
│       ├── Subscription.java
│       ├── BillingCycle.java         # enum: WEEKLY, MONTHLY, QUARTERLY, YEARLY
│       ├── SubscriptionStatus.java   # enum: ACTIVE, PAUSED, CANCELLED
│       ├── SubscriptionRepository.java
│       └── SubscriptionService.java
│
├── reporting/
│   ├── BalanceService.java           # compute account balance from journal lines
│   ├── ReportingService.java         # cash flow, net worth, spending by category
│   └── dto/                          # all DTO projections live here
│       ├── AccountBalanceDto.java
│       ├── CashFlowDto.java
│       ├── SpendingByCategoryDto.java
│       └── NetWorthDto.java
│
├── mcp/
│   └── tools/
│       ├── AccountTools.java         # @Tool methods for account management
│       ├── TransactionTools.java     # @Tool methods for recording transactions
│       ├── DebtTools.java            # @Tool methods for debts and lendings
│       ├── LoanTools.java            # @Tool methods for loans
│       ├── SubscriptionTools.java    # @Tool methods for subscriptions
│       └── ReportingTools.java       # @Tool methods for reports
│
└── init/
    └── DefaultAccountSeeder.java     # seeds system accounts for new users

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_users.sql
    ├── V2__create_accounts.sql
    ├── V3__create_tags.sql
    ├── V4__create_journal_entries.sql
    ├── V5__create_journal_entry_lines.sql
    ├── V6__create_journal_entry_tags.sql
    ├── V7__create_people.sql
    ├── V8__create_debt_records.sql
    ├── V9__create_loans.sql
    ├── V10__create_subscriptions.sql
    ├── V11__create_indexes.sql
    ├── V12__enable_rls.sql
    └── V13__create_materialized_views.sql
```

---

## MCP Tools Surface

```
Account Management
  add_account(name, type, parentId?)               → Account
  list_accounts(type?)                             → List<Account>
  get_account_balance(accountId, asOfDate?)        → BigDecimal

Transactions
  record_transaction(date, description, lines, tags?)
      lines: [{accountId, amount, side: DEBIT|CREDIT}]  → JournalEntry
  get_transactions(accountId?, from?, to?, tag?)   → List<JournalEntryDto>

People
  add_person(name, phone?, email?)                 → Person
  list_people()                                    → List<Person>

Debts & Lendings
  add_debt(personId, direction, amount, dueDate?)  → DebtRecord + Account created
  get_debts(direction?, personId?)                 → List<DebtSummaryDto>
      (includes current outstanding balance from journal)

Loans
  add_loan(name, direction, principal, interestRate, emiAmount, startDate, endDate)
  record_loan_payment(loanId, amount, principalPortion, interestPortion, date)
  get_loan_summary(loanId)                         → LoanSummaryDto

Subscriptions
  add_subscription(name, amount, cycle, paymentAccountId, expenseAccountId)
  get_upcoming_bills(days?)                        → List<SubscriptionDto>
  update_subscription_status(id, status)

Reporting
  get_spending_report(from, to, groupBy: account|tag) → List<SpendingDto>
  get_net_worth(asOfDate?)                         → NetWorthDto
  get_cash_flow(from, to)                          → CashFlowDto
```

---

## Implementation Phases

```
Phase 1   Spring Boot scaffold
          Add Flyway to build.gradle. Configure application.yml:
          HikariCP pool sizing, Hibernate batch settings, Flyway locations,
          OAuth2 JWT issuer. Verify app starts against a local PostgreSQL.

Phase 2   Flyway migrations
          All table DDL, RLS enable + policies, composite indexes,
          materialized view for monthly account summaries.

Phase 3   Auth + RLS wiring
          TenantContext (ThreadLocal). RlsInterceptor sets
          SET LOCAL app.current_user_id before each transaction.
          SecurityConfig: stateless JWT via OAuth2 resource server.
          Extract userId from JWT sub claim into TenantContext.

Phase 4   Account domain
          Account entity with UUID v7, enums, self-ref parent.
          AccountRepository. AccountService.
          DefaultAccountSeeder: seeds system accounts on first user login.

Phase 5   Double-entry core
          JournalEntry + JournalEntryLine entities.
          AccountingService.record() validates balance before persisting.
          BalanceService computes account balance via JPQL DTO projection.

Phase 6   People / Debt / Loan / Subscription
          Entities, repos, services for each domain.
          DebtService creates the Receivable/Payable account automatically.
          LoanService: record_loan_payment creates a 3-leg journal entry
          (principal reduces loan liability, interest goes to expense, cash out).

Phase 7   Reporting
          ReportingService: getCashFlow, getSpendingByCategory, getNetWorth.
          All queries use constructor expressions — never entity loads.
          Materialized view refresh on report generation.

Phase 8   MCP tools
          @Tool methods in each tools class, wired to service layer.
          Structured input/output types per tool.
          Spring AI MCP server auto-discovery of @Tool beans.
```

---

## Key Invariants to Never Break

1. Every journal entry must have `SUM(DEBIT amounts) == SUM(CREDIT amounts)` — enforced
   in `AccountingService.record()` before any DB write.

2. Account balance is never stored — always computed from journal lines.
   There is no `balance` column on the `accounts` table.

3. Debt/lending status is never stored — a debt is settled when the account balance
   reaches zero. No `status` field on `debt_records`.

4. All `@OneToMany` associations are `LAZY`. No exceptions.

5. Service methods that only read data carry `@Transactional(readOnly = true)`.

6. The RLS interceptor must fire before every query. If `TenantContext` is empty
   (e.g., in a scheduled job), the interceptor must throw, not silently skip.
