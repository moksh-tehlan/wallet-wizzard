package com.moksh.walletwizzard.reporting;

import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.reporting.dto.AccountBalanceDto;
import com.moksh.walletwizzard.reporting.dto.CashFlowDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyReportDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyTrendDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.SpendingAverageDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByCategoryDto;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
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
    // Net Worth
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Total assets minus total liabilities as of the given date (defaults to today).
     * Positive net worth = assets exceed liabilities.
     */
    public NetWorthDto getNetWorth(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

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
                """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("asOfDate", effectiveDate)
                .getSingleResult();

        return NetWorthDto.of(effectiveDate, decimal(row[0]), decimal(row[1]));
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
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Safely converts a native query numeric result to BigDecimal. */
    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
