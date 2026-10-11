package com.moksh.walletwizzard.reporting;

import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.reporting.dto.AccountBalanceDto;
import com.moksh.walletwizzard.reporting.dto.CashFlowDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyReportDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyTrendDto;
import com.moksh.walletwizzard.reporting.dto.LiquidNetWorthDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.SpendingAverageDto;
import com.moksh.walletwizzard.reporting.dto.CashFlowProjectionDto;
import com.moksh.walletwizzard.reporting.dto.FinancialHealthDto;
import com.moksh.walletwizzard.reporting.dto.MerchantCategorySuggestionDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByCategoryDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByMerchantDto;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All methods are read-only projections — no managed entities are loaded.
 * RLS is enforced automatically because every query goes through the
 * EntityManager, which borrows from our RlsDataSource-wrapped HikariCP pool.
 *
 * The shared balance expression:
 *   CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
 * computes the signed contribution of each journal line to its account's balance.
 * This mirrors AccountRepository.computeBalance but at an aggregate level.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportingService {

    private final EntityManager em;

    // ─────────────────────────────────────────────────────────────────────────
    // Cash Flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Total income and expenses for a date range with net savings.
     * Only lines whose account type is INCOME or EXPENSE are counted.
     */
    public CashFlowDto getCashFlow(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN a.type = 'INCOME'
                        THEN CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                        ELSE 0 END), 0) AS total_income,
                    COALESCE(SUM(CASE WHEN a.type = 'EXPENSE'
                        THEN CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                        ELSE 0 END), 0) AS total_expenses
                FROM journal_entry_lines jel
                JOIN accounts a  ON jel.account_id       = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type IN ('INCOME', 'EXPENSE')
                  AND (:from IS NULL OR je.date >= CAST(:from AS date))
                  AND (:to   IS NULL OR je.date <= CAST(:to   AS date))
                """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        return CashFlowDto.of(from, to, decimal(row[0]), decimal(row[1]));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spending by Category
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Expense breakdown by account, ordered by amount descending.
     * Only accounts with a positive net expense in the period are returned.
     * percentageOfTotal is computed in Java after the query (requires the total).
     */
    public List<SpendingByCategoryDto> getSpendingByCategory(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    a.id::text           AS account_id,
                    a.name               AS account_name,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0)                AS amount
                FROM journal_entry_lines jel
                JOIN accounts       a  ON jel.account_id       = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type = 'EXPENSE'
                  AND (:from IS NULL OR je.date >= CAST(:from AS date))
                  AND (:to   IS NULL OR je.date <= CAST(:to   AS date))
                GROUP BY a.id, a.name
                HAVING COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0) > 0
                ORDER BY amount DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        BigDecimal total = rows.stream()
                .map(r -> decimal(r[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
                .map(r -> SpendingByCategoryDto.of(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        decimal(r[2]),
                        total))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spending by Merchant
    // ─────────────────────────────────────────────────────────────────────────

    public List<SpendingByMerchantDto> getSpendingByMerchant(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    je.merchant,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0) AS amount,
                    COUNT(DISTINCT je.id) AS txn_count
                FROM journal_entry_lines jel
                JOIN accounts       a  ON jel.account_id       = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type = 'EXPENSE'
                  AND je.merchant IS NOT NULL
                  AND (:from IS NULL OR je.date >= CAST(:from AS date))
                  AND (:to   IS NULL OR je.date <= CAST(:to   AS date))
                GROUP BY je.merchant
                HAVING COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0) > 0
                ORDER BY amount DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        BigDecimal total = rows.stream()
                .map(r -> decimal(r[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
                .map(r -> SpendingByMerchantDto.of(
                        (String) r[0],
                        decimal(r[1]),
                        ((Number) r[2]).intValue(),
                        total))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Net Worth
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Total assets minus total liabilities as of the given date (defaults to today).
     * Positive net worth = assets exceed liabilities.
     */
    public NetWorthDto getNetWorth(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        // Investment accounts are excluded here; their cost basis is added separately
        // from investments.invested_amount so that unfunded (pre-existing) investments
        // are properly counted without relying on a self-cancelling journal entry.
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN a.type = 'ASSET'
                        THEN CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                        ELSE 0 END), 0) AS total_assets,
                    COALESCE(SUM(CASE WHEN a.type = 'LIABILITY'
                        THEN CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                        ELSE 0 END), 0) AS total_liabilities
                FROM journal_entry_lines jel
                JOIN accounts       a  ON jel.account_id       = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type IN ('ASSET', 'LIABILITY')
                  AND je.date <= CAST(:asOfDate AS date)
                  AND a.id NOT IN (SELECT account_id FROM investments)
                """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("asOfDate", effectiveDate)
                .getSingleResult();

        // Co-borrower receivables: for TAKEN loans, each participant owes their share
        // of the *current outstanding loan balance* (remaining principal on the account).
        String participantSql = """
                SELECT COALESCE(SUM(bal.outstanding * lp.share_percent / 100.0), 0)
                FROM loan_participants lp
                JOIN loans l ON l.id = lp.loan_id
                JOIN (
                    SELECT
                        jel.account_id,
                        SUM(CASE WHEN jel.side = a.normal_balance
                                 THEN jel.amount ELSE -jel.amount END) AS outstanding
                    FROM journal_entry_lines jel
                    JOIN accounts       a  ON jel.account_id       = a.id
                    JOIN journal_entries je ON jel.journal_entry_id = je.id
                    WHERE je.date <= CAST(:asOfDate AS date)
                    GROUP BY jel.account_id
                ) bal ON bal.account_id = l.account_id
                WHERE l.direction = 'TAKEN'
                """;

        BigDecimal sharedLoanReceivables = decimal(
                em.createNativeQuery(participantSql)
                        .setParameter("asOfDate", effectiveDate)
                        .getSingleResult()
        );

        // Pull invested_amount (cost basis) and market gains from the investments table.
        // invested_amount is added to totalAssets; gains are shown separately.
        String investmentSql = """
                SELECT
                    COALESCE(SUM(invested_amount), 0)              AS total_invested,
                    COALESCE(SUM(current_value - invested_amount), 0) AS total_gains
                FROM investments
                """;
        Object[] invRow = (Object[]) em.createNativeQuery(investmentSql).getSingleResult();
        BigDecimal totalInvested  = decimal(invRow[0]);
        BigDecimal investmentGains = decimal(invRow[1]);

        BigDecimal totalAssets = decimal(row[0]).add(totalInvested);
        return NetWorthDto.of(effectiveDate, totalAssets, decimal(row[1]), sharedLoanReceivables, investmentGains);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Liquid Net Worth
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cash + receivables + investments minus credit cards and short-term liabilities.
     * Excludes long-term loans (LOAN_PAYABLE) entirely — shows what you'd have if
     * you cleared all short-term obligations today.
     */
    public LiquidNetWorthDto getLiquidNetWorth() {
        LocalDate today = LocalDate.now();

        String sql = """
                SELECT
                    a.sub_type,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0) AS balance
                FROM accounts a
                LEFT JOIN journal_entry_lines jel ON jel.account_id = a.id
                WHERE a.is_active = true
                  AND a.sub_type IN ('BANK_ACCOUNT','CASH_AND_WALLET','RECEIVABLE',
                                     'CREDIT_CARD','OTHER_LIABILITY')
                GROUP BY a.sub_type
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();

        BigDecimal cashAndBanks = BigDecimal.ZERO;
        BigDecimal receivables  = BigDecimal.ZERO;
        BigDecimal creditCards  = BigDecimal.ZERO;
        BigDecimal otherLiab    = BigDecimal.ZERO;

        for (Object[] r : rows) {
            String sub = (String) r[0];
            BigDecimal bal = decimal(r[1]);
            switch (sub) {
                case "BANK_ACCOUNT", "CASH_AND_WALLET" -> cashAndBanks = cashAndBanks.add(bal);
                case "RECEIVABLE"                       -> receivables  = receivables.add(bal);
                case "CREDIT_CARD"                      -> creditCards  = creditCards.add(bal);
                case "OTHER_LIABILITY"                  -> otherLiab    = otherLiab.add(bal);
            }
        }

        String invSql = "SELECT COALESCE(SUM(current_value), 0) FROM investments";
        BigDecimal investments = decimal(em.createNativeQuery(invSql).getSingleResult());

        // What account participants owe you: their share% of each shared liability's balance
        String participantSql = """
                SELECT COALESCE(SUM(bal.balance * ap.share_percent / 100.0), 0)
                FROM account_participants ap
                JOIN accounts a ON a.id = ap.account_id
                JOIN (
                    SELECT jel.account_id,
                           SUM(CASE WHEN jel.side = a2.normal_balance
                                    THEN jel.amount ELSE -jel.amount END) AS balance
                    FROM journal_entry_lines jel
                    JOIN accounts a2 ON jel.account_id = a2.id
                    GROUP BY jel.account_id
                ) bal ON bal.account_id = ap.account_id
                WHERE a.type = 'LIABILITY'
                  AND a.is_active = true
                  AND bal.balance > 0
                """;
        BigDecimal participantReceivables = decimal(em.createNativeQuery(participantSql).getSingleResult());

        return LiquidNetWorthDto.of(today, cashAndBanks, receivables, participantReceivables, investments, creditCards, otherLiab);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Monthly Report
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full summary for one calendar month: income, expenses, savings rate,
     * and a full spending breakdown by expense category.
     */
    public MonthlyReportDto getMonthlyReport(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        CashFlowDto cashFlow = getCashFlow(from, to);
        List<SpendingByCategoryDto> breakdown = getSpendingByCategory(from, to);

        return new MonthlyReportDto(year, month,
                cashFlow.totalIncome(),
                cashFlow.totalExpenses(),
                cashFlow.netSavings(),
                breakdown);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spending Averages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Average monthly spend per expense category over the last N months.
     * Only months that have at least one expense entry for that category are counted
     * in the average (avoids diluting the average with months you didn't use the category).
     */
    public List<SpendingAverageDto> getSpendingAverages(int months) {
        LocalDate from = LocalDate.now().minusMonths(months).withDayOfMonth(1);

        String sql = """
                SELECT
                    sub.account_id::text,
                    sub.account_name,
                    AVG(sub.monthly_total)  AS avg_spend,
                    MAX(sub.monthly_total)  AS max_spend,
                    MIN(sub.monthly_total)  AS min_spend,
                    COUNT(*)                AS months_with_data
                FROM (
                    SELECT
                        a.id        AS account_id,
                        a.name      AS account_name,
                        DATE_TRUNC('month', je.date) AS month,
                        SUM(CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END) AS monthly_total
                    FROM journal_entry_lines jel
                    JOIN accounts       a  ON jel.account_id       = a.id
                    JOIN journal_entries je ON jel.journal_entry_id = je.id
                    WHERE a.type = 'EXPENSE'
                      AND je.date >= CAST(:from AS date)
                    GROUP BY a.id, a.name, DATE_TRUNC('month', je.date)
                    HAVING SUM(CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END) > 0
                ) sub
                GROUP BY sub.account_id, sub.account_name
                ORDER BY avg_spend DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("from", from)
                .getResultList();

        return rows.stream()
                .map(r -> new SpendingAverageDto(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        decimal(r[2]).setScale(2, RoundingMode.HALF_UP),
                        decimal(r[3]).setScale(2, RoundingMode.HALF_UP),
                        decimal(r[4]).setScale(2, RoundingMode.HALF_UP),
                        ((Number) r[5]).intValue()
                ))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Monthly Trend
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Month-by-month income vs expenses for the last N complete months (most recent first).
     * Also computes averages across all months in the range.
     */
    public MonthlyTrendDto getMonthlyTrend(int months) {
        List<MonthlyTrendDto.MonthRow> rows = new ArrayList<>(months);
        YearMonth current = YearMonth.now().minusMonths(1); // start from last complete month

        for (int i = 0; i < months; i++) {
            YearMonth ym = current.minusMonths(i);
            CashFlowDto flow = getCashFlow(ym.atDay(1), ym.atEndOfMonth());
            rows.add(new MonthlyTrendDto.MonthRow(
                    ym.toString(),
                    flow.totalIncome(),
                    flow.totalExpenses(),
                    flow.netSavings()
            ));
        }

        BigDecimal avgIncome = rows.stream()
                .map(MonthlyTrendDto.MonthRow::income)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        BigDecimal avgExpenses = rows.stream()
                .map(MonthlyTrendDto.MonthRow::expenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        return new MonthlyTrendDto(months, avgIncome, avgExpenses, avgIncome.subtract(avgExpenses), rows);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account Balances
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * All active accounts with their current balances, optionally filtered by type.
     * Accounts with zero balance are included (they may still be relevant).
     */
    public List<AccountBalanceDto> getAccountBalances(AccountType type, AccountSubType subType) {
        if (subType != null) {
            return getAccountBalancesBySubType(subType);
        }
        if (type != null) {
            return getAccountBalancesByType(type);
        }
        return getAllAccountBalances();
    }

    private List<AccountBalanceDto> getAllAccountBalances() {
        String sql = """
                SELECT
                    a.id::text   AS account_id,
                    a.name       AS account_name,
                    a.type       AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0)        AS balance
                FROM accounts a
                LEFT JOIN journal_entry_lines jel ON jel.account_id = a.id
                WHERE a.is_active = true
                GROUP BY a.id, a.name, a.type
                ORDER BY a.type, a.name
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return toAccountBalanceDtos(rows);
    }

    private List<AccountBalanceDto> getAccountBalancesByType(AccountType type) {
        String sql = """
                SELECT
                    a.id::text   AS account_id,
                    a.name       AS account_name,
                    a.type       AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0)        AS balance
                FROM accounts a
                LEFT JOIN journal_entry_lines jel ON jel.account_id = a.id
                WHERE a.is_active = true
                  AND a.type = :type
                GROUP BY a.id, a.name, a.type
                ORDER BY a.type, a.name
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("type", type.name())
                .getResultList();
        return toAccountBalanceDtos(rows);
    }

    private List<AccountBalanceDto> getAccountBalancesBySubType(AccountSubType subType) {
        String sql = """
                SELECT
                    a.id::text   AS account_id,
                    a.name       AS account_name,
                    a.type       AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                    ), 0)        AS balance
                FROM accounts a
                LEFT JOIN journal_entry_lines jel ON jel.account_id = a.id
                WHERE a.is_active = true
                  AND a.sub_type = :subType
                GROUP BY a.id, a.name, a.type
                ORDER BY a.name
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("subType", subType.name())
                .getResultList();
        return toAccountBalanceDtos(rows);
    }

    private List<AccountBalanceDto> toAccountBalanceDtos(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new AccountBalanceDto(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        AccountType.valueOf((String) r[2]),
                        decimal(r[3])))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cash Flow Projection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Projects month-by-month cash balance based on current bank balance,
     * upcoming recurring transactions, and scheduled loan installments.
     */
    public CashFlowProjectionDto getCashFlowProjection(int months) {
        // Current liquid balance (banks + cash wallets only)
        String balSql = """
                SELECT COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0)
                FROM journal_entry_lines jel
                JOIN accounts a ON jel.account_id = a.id
                WHERE a.sub_type IN ('BANK_ACCOUNT','CASH_AND_WALLET')
                  AND a.is_active = true
                """;
        BigDecimal currentBalance = decimal(em.createNativeQuery(balSql).getSingleResult());

        // Upcoming recurring transactions (ACTIVE, has nextBillingDate)
        String recurSql = """
                SELECT s.name, s.amount, s.side, s.next_billing_date, s.billing_cycle
                FROM subscriptions s
                WHERE s.status = 'ACTIVE'
                  AND s.next_billing_date IS NOT NULL
                ORDER BY s.next_billing_date
                """;
        @SuppressWarnings("unchecked")
        List<Object[]> recurRows = em.createNativeQuery(recurSql).getResultList();

        // Upcoming scheduled installments with auto-pay configured
        String instSql = """
                SELECT l.name, li.total_amount, li.due_date
                FROM loan_installments li
                JOIN loans l ON l.id = li.loan_id
                WHERE li.status IN ('SCHEDULED','DUE')
                  AND l.default_bank_account_id IS NOT NULL
                  AND li.due_date >= CURRENT_DATE
                ORDER BY li.due_date
                """;
        @SuppressWarnings("unchecked")
        List<Object[]> instRows = em.createNativeQuery(instSql).getResultList();

        LocalDate today = LocalDate.now();
        List<CashFlowProjectionDto.MonthProjection> projections = new ArrayList<>(months);
        BigDecimal runningBalance = currentBalance;

        for (int i = 0; i < months; i++) {
            YearMonth ym = YearMonth.now().plusMonths(i);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            BigDecimal inflows = BigDecimal.ZERO;
            BigDecimal outflows = BigDecimal.ZERO;
            List<String> inflowItems = new ArrayList<>();
            List<String> outflowItems = new ArrayList<>();

            for (Object[] row : recurRows) {
                String name = (String) row[0];
                BigDecimal amount = decimal(row[1]);
                String side = (String) row[2];
                LocalDate nextDate = ((java.sql.Date) row[3]).toLocalDate();

                // Estimate how many times this recurs in the month
                if (!nextDate.isAfter(monthEnd) && !nextDate.isBefore(monthStart)) {
                    if ("DEBIT".equals(side)) {
                        outflows = outflows.add(amount);
                        outflowItems.add(name + " -₹" + amount.setScale(0, RoundingMode.HALF_UP));
                    } else {
                        inflows = inflows.add(amount);
                        inflowItems.add(name + " +₹" + amount.setScale(0, RoundingMode.HALF_UP));
                    }
                }
            }

            for (Object[] row : instRows) {
                String loanName = (String) row[0];
                BigDecimal amount = decimal(row[1]);
                LocalDate dueDate = ((java.sql.Date) row[2]).toLocalDate();
                if (!dueDate.isBefore(monthStart) && !dueDate.isAfter(monthEnd)) {
                    outflows = outflows.add(amount);
                    outflowItems.add(loanName + " EMI -₹" + amount.setScale(0, RoundingMode.HALF_UP));
                }
            }

            BigDecimal closingBalance = runningBalance.add(inflows).subtract(outflows);
            projections.add(new CashFlowProjectionDto.MonthProjection(
                    ym.toString(), runningBalance, inflows, outflows,
                    closingBalance, inflowItems, outflowItems));
            runningBalance = closingBalance;
        }

        return new CashFlowProjectionDto(currentBalance, projections);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Merchant Category Suggestion
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the top expense accounts used for a given merchant, ordered by frequency.
     * Useful for auto-suggesting the category when logging a new transaction.
     */
    public MerchantCategorySuggestionDto suggestCategoryForMerchant(String merchant) {
        String sql = """
                SELECT
                    a.id::text AS account_id,
                    a.name     AS account_name,
                    COUNT(*)   AS use_count
                FROM journal_entries je
                JOIN journal_entry_lines jel ON jel.journal_entry_id = je.id
                JOIN accounts a ON jel.account_id = a.id
                WHERE UPPER(je.merchant) = UPPER(:merchant)
                  AND a.type = 'EXPENSE'
                  AND jel.side = a.normal_balance
                GROUP BY a.id, a.name
                ORDER BY use_count DESC
                LIMIT 5
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("merchant", merchant)
                .getResultList();

        int total = rows.stream().mapToInt(r -> ((Number) r[2]).intValue()).sum();

        List<MerchantCategorySuggestionDto.Suggestion> suggestions = rows.stream()
                .map(r -> new MerchantCategorySuggestionDto.Suggestion(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        ((Number) r[2]).intValue(),
                        total == 0 ? 0.0 : Math.round(((Number) r[2]).doubleValue() * 1000.0 / total) / 10.0
                ))
                .toList();

        return new MerchantCategorySuggestionDto(merchant, suggestions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Financial Health Score
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes a 0–100 financial health score based on emergency fund coverage,
     * savings rate, and debt-to-income ratio using the last 3 months of data.
     */
    public FinancialHealthDto getFinancialHealthScore() {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        // Cash + banks
        String cashSql = """
                SELECT COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0)
                FROM journal_entry_lines jel
                JOIN accounts a ON jel.account_id = a.id
                WHERE a.sub_type IN ('BANK_ACCOUNT','CASH_AND_WALLET')
                  AND a.is_active = true
                """;
        BigDecimal cashAndBanks = decimal(em.createNativeQuery(cashSql).getSingleResult());

        // Average monthly income (last 3 months)
        String incomeSql = """
                SELECT COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0) / 3.0
                FROM journal_entry_lines jel
                JOIN accounts a ON jel.account_id = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type = 'INCOME'
                  AND je.date >= :from
                """;
        BigDecimal monthlyIncome = decimal(em.createNativeQuery(incomeSql)
                .setParameter("from", threeMonthsAgo).getSingleResult())
                .setScale(2, RoundingMode.HALF_UP);

        // Average monthly expenses (last 3 months)
        String expSql = """
                SELECT COALESCE(SUM(
                    CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
                ), 0) / 3.0
                FROM journal_entry_lines jel
                JOIN accounts a ON jel.account_id = a.id
                JOIN journal_entries je ON jel.journal_entry_id = je.id
                WHERE a.type = 'EXPENSE'
                  AND je.date >= :from
                """;
        BigDecimal monthlyExpenses = decimal(em.createNativeQuery(expSql)
                .setParameter("from", threeMonthsAgo).getSingleResult())
                .setScale(2, RoundingMode.HALF_UP);

        // Monthly EMI total (scheduled installments per month)
        String emiSql = """
                SELECT COALESCE(SUM(li.total_amount), 0) / NULLIF(COUNT(DISTINCT DATE_TRUNC('month', li.due_date)), 0)
                FROM loan_installments li
                JOIN loans l ON l.id = li.loan_id
                WHERE li.status = 'PAID'
                  AND li.paid_date >= :from
                  AND l.direction = 'TAKEN'
                """;
        BigDecimal monthlyEmi = decimal(em.createNativeQuery(emiSql)
                .setParameter("from", threeMonthsAgo).getSingleResult())
                .setScale(2, RoundingMode.HALF_UP);

        // Emergency fund: months of expenses covered
        double monthsCovered = monthlyExpenses.compareTo(BigDecimal.ZERO) == 0 ? 99.0
                : cashAndBanks.divide(monthlyExpenses, 2, RoundingMode.HALF_UP).doubleValue();
        String efStatus = monthsCovered < 1 ? "CRITICAL" : monthsCovered < 3 ? "LOW"
                : monthsCovered < 6 ? "ADEQUATE" : "STRONG";
        int efScore = (int) Math.min(monthsCovered / 6.0 * 40, 40); // max 40 pts

        // Savings rate
        double savingsRate = monthlyIncome.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : monthlyIncome.subtract(monthlyExpenses)
                        .divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
        String srStatus = savingsRate < 10 ? "LOW" : savingsRate < 20 ? "MODERATE"
                : savingsRate < 30 ? "GOOD" : "EXCELLENT";
        int srScore = (int) Math.min(savingsRate / 30.0 * 35, 35); // max 35 pts

        // Debt-to-income
        double dti = monthlyIncome.compareTo(BigDecimal.ZERO) == 0 ? 100.0
                : monthlyEmi.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
        String dtiStatus = dti < 35 ? "HEALTHY" : dti < 50 ? "MODERATE" : "HIGH";
        int dtiScore = (int) Math.max(25 - (dti / 50.0 * 25), 0); // max 25 pts

        int overallScore = Math.min(efScore + srScore + dtiScore, 100);
        String grade = overallScore >= 80 ? "A" : overallScore >= 65 ? "B"
                : overallScore >= 50 ? "C" : overallScore >= 35 ? "D" : "F";

        List<String> recommendations = new ArrayList<>();
        if ("CRITICAL".equals(efStatus) || "LOW".equals(efStatus)) {
            recommendations.add("Build emergency fund to at least 3 months of expenses (target: ₹"
                    + monthlyExpenses.multiply(BigDecimal.valueOf(3)).setScale(0, RoundingMode.HALF_UP) + ")");
        }
        if ("LOW".equals(srStatus)) {
            recommendations.add("Increase savings rate — currently " + String.format("%.1f", savingsRate) + "%, target ≥20%");
        }
        if ("HIGH".equals(dtiStatus)) {
            recommendations.add("EMI burden is " + String.format("%.1f", dti) + "% of income — consider prepaying high-interest loans");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Finances look healthy — consider investing surplus for long-term wealth building");
        }

        return new FinancialHealthDto(
                overallScore, grade,
                new FinancialHealthDto.EmergencyFund(cashAndBanks, monthlyExpenses, monthsCovered, efStatus),
                new FinancialHealthDto.SavingsRate(monthlyIncome, monthlyExpenses, savingsRate, srStatus),
                new FinancialHealthDto.DebtBurden(monthlyEmi, monthlyIncome, dti, dtiStatus),
                recommendations
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Safely converts a native query numeric result to BigDecimal. */
    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
