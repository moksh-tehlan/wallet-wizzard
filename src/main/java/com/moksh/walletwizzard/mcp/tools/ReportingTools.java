package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.reporting.ReportingService;
import com.moksh.walletwizzard.reporting.dto.CashFlowDto;
import com.moksh.walletwizzard.reporting.dto.CashFlowProjectionDto;
import com.moksh.walletwizzard.reporting.dto.FinancialHealthDto;
import com.moksh.walletwizzard.reporting.dto.MerchantCategorySuggestionDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyReportDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyTrendDto;
import com.moksh.walletwizzard.reporting.dto.LiquidNetWorthDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthHistoryDto;
import com.moksh.walletwizzard.reporting.dto.SpendingAverageDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByCategoryDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByMerchantDto;
import com.moksh.walletwizzard.service.NetWorthSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportingTools {

    private final ReportingService reportingService;
    private final NetWorthSnapshotService netWorthSnapshotService;

    @McpTool(
            name = "get_cash_flow",
            description = """
                    Returns income vs expenses for a date range with net savings.
                    netSavings = totalIncome − totalExpenses (negative = overspent).
                    Omit from/to for all-time totals.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public CashFlowDto getCashFlow(
            @McpArg(name = "from", description = "Start date YYYY-MM-DD (optional)", required = false) String from,
            @McpArg(name = "to", description = "End date YYYY-MM-DD (optional)", required = false) String to
    ) {
        return reportingService.getCashFlow(
                McpInputs.parseDate(from, "from"),
                McpInputs.parseDate(to, "to")
        );
    }

    @McpTool(
            name = "get_liquid_net_worth",
            description = """
                    Returns your true liquid financial position — what you'd actually have if you
                    cleared all short-term obligations today.

                    Includes:
                      cashAndBanks     — all BANK_ACCOUNT + CASH_AND_WALLET balances
                      receivables      — money others owe you (informal debts)
                      investments      — current market value of all investments
                      creditCardDebt   — total outstanding credit card balances (shown as positive = what you owe)
                      otherLiabilities — any OTHER_LIABILITY accounts
                      liquidNetWorth   — cashAndBanks + receivables + investments − creditCardDebt − otherLiabilities

                    Excludes long-term loans (LOAN_PAYABLE) entirely — use get_net_worth for the full
                    picture including loans and shared loan receivables from co-borrowers.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public LiquidNetWorthDto getLiquidNetWorth() {
        return reportingService.getLiquidNetWorth();
    }

    @McpTool(
            name = "get_spending_by_category",
            description = """
                    Breaks down expenses by category (expense account) for a period.
                    Results are ordered by amount descending, each with a percentage of total.
                    Useful for: 'What did I spend most on this month?'
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<SpendingByCategoryDto> getSpendingByCategory(
            @McpArg(name = "from", description = "Start date YYYY-MM-DD (optional)", required = false) String from,
            @McpArg(name = "to", description = "End date YYYY-MM-DD (optional)", required = false) String to
    ) {
        return reportingService.getSpendingByCategory(
                McpInputs.parseDate(from, "from"),
                McpInputs.parseDate(to, "to")
        );
    }

    @McpTool(
            name = "get_spending_by_merchant",
            description = """
                    Breaks down expenses by merchant/vendor for a period.
                    Only transactions logged with a merchant field are included.
                    Results ordered by amount descending, each with percentage of merchant-tagged total.

                    Useful for: 'How much did I spend on Zomato this month?'
                               'Which app am I spending most on?'
                               'Compare Blinkit vs Zepto spend'

                    Use with from/to for a specific period, or omit for all-time.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<SpendingByMerchantDto> getSpendingByMerchant(
            @McpArg(name = "from", description = "Start date YYYY-MM-DD (optional)", required = false) String from,
            @McpArg(name = "to", description = "End date YYYY-MM-DD (optional)", required = false) String to
    ) {
        return reportingService.getSpendingByMerchant(
                McpInputs.parseDate(from, "from"),
                McpInputs.parseDate(to, "to")
        );
    }

    @McpTool(
            name = "get_net_worth",
            description = """
                    Returns total assets, liabilities, and net worth.
                    netWorth = totalAssets − totalLiabilities + sharedLoanReceivables + investmentGains.
                    totalAssets includes bank/cash balances PLUS the invested cost basis of all investments.
                    sharedLoanReceivables = remaining loan balance × co-borrower share%.
                    investmentGains = SUM(currentValue − investedAmount) across all investments (can be negative).
                    Call refresh_investment_values first for fresh MF NAVs.
                    Pass asOfDate for a historical snapshot; omit for current net worth.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public NetWorthDto getNetWorth(
            @McpArg(name = "asOfDate", description = "Historical date YYYY-MM-DD (omit for today)", required = false) String asOfDate
    ) {
        return reportingService.getNetWorth(McpInputs.parseDate(asOfDate, "asOfDate"));
    }

    @McpTool(
            name = "get_monthly_report",
            description = """
                    Full financial summary for one calendar month.
                    Includes income, expenses, net savings, and a spending breakdown by category.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public MonthlyReportDto getMonthlyReport(
            @McpArg(name = "year", description = "4-digit year e.g. 2026", required = true) int year,
            @McpArg(name = "month", description = "Month number 1-12", required = true) int month
    ) {
        return reportingService.getMonthlyReport(year, month);
    }

    @McpTool(
            name = "get_spending_averages",
            description = """
                    Returns average monthly spend per expense category over the last N months.
                    Useful for establishing spending baselines and spotting categories where you
                    consistently over- or under-spend.

                    For each category: averageMonthlySpend, maxMonthSpend, minMonthSpend,
                    and monthsWithData (only months with actual spend in that category are counted,
                    so the average isn't diluted by months you didn't use it).

                    months defaults to 6. Use 3 for a recent trend, 12 for a yearly baseline.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<SpendingAverageDto> getSpendingAverages(
            @McpArg(name = "months", description = "Number of past months to analyse (default 6, max 24)", required = false)
            Integer months
    ) {
        int m = months != null ? Math.min(Math.max(months, 1), 24) : 6;
        return reportingService.getSpendingAverages(m);
    }

    @McpTool(
            name = "get_financial_year_summary",
            description = """
                    Returns a cash flow summary for the current Indian financial year (April 1 – March 31).
                    Automatically computes the correct FY boundaries based on today's date.
                    Also includes spending by category for the same period.
                    Use for tax filing season, annual reviews, or ITR preparation.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public Map<String, Object> getFinancialYearSummary() {
        LocalDate today = LocalDate.now();
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate from = LocalDate.of(fyStartYear, 4, 1);
        LocalDate to = LocalDate.of(fyStartYear + 1, 3, 31);

        CashFlowDto cashFlow = reportingService.getCashFlow(from, to);
        List<SpendingByCategoryDto> breakdown = reportingService.getSpendingByCategory(from, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("financialYear", "FY" + fyStartYear + "-" + (fyStartYear + 1));
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("cashFlow", cashFlow);
        result.put("spendingByCategory", breakdown);
        return result;
    }

    @McpTool(
            name = "get_monthly_trend",
            description = """
                    Month-by-month income vs expenses for the last N complete months, most recent first.
                    Also returns averages across the entire period.
                    Use this to answer: 'Am I spending more or less than usual lately?' or
                    'What is my average savings rate over the last 6 months?'

                    months defaults to 6.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public MonthlyTrendDto getMonthlyTrend(
            @McpArg(name = "months", description = "Number of past months to show (default 6, max 24)", required = false)
            Integer months
    ) {
        int m = months != null ? Math.min(Math.max(months, 1), 24) : 6;
        return reportingService.getMonthlyTrend(m);
    }

    @McpTool(
            name = "get_cash_flow_projection",
            description = """
                    Projects your month-by-month cash balance for the next N months.
                    Based on: current bank balance + upcoming recurring transactions (salary, bills)
                    + scheduled loan EMIs.

                    Each month shows:
                      openingBalance  — balance at start of month
                      totalInflows    — expected income (salary, recurring credits)
                      totalOutflows   — expected bills + EMIs
                      closingBalance  — projected end-of-month balance
                      inflowItems / outflowItems — line-by-line breakdown

                    months defaults to 3. Use this instead of manual calculations.
                    Note: does not include variable expenses (food, shopping, etc.).
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public CashFlowProjectionDto getCashFlowProjection(
            @McpArg(name = "months", description = "Number of months to project (default 3, max 12)", required = false) Integer months
    ) {
        int m = months != null ? Math.min(Math.max(months, 1), 12) : 3;
        return reportingService.getCashFlowProjection(m);
    }

    @McpTool(
            name = "get_net_worth_history",
            description = """
                    Returns historical net worth snapshots over the last N months.
                    Snapshots are recorded daily by the auto-pay scheduler.
                    Also shows total change in net worth (amount + %) over the period.

                    Use this to answer: 'Is my wealth growing over time?'
                    months defaults to 6.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public NetWorthHistoryDto getNetWorthHistory(
            @McpArg(name = "months", description = "Months of history to show (default 6, max 24)", required = false) Integer months
    ) {
        int m = months != null ? Math.min(Math.max(months, 1), 24) : 6;
        return netWorthSnapshotService.getHistory(m);
    }

    @McpTool(
            name = "suggest_category_for_merchant",
            description = """
                    Suggests expense account(s) to use when logging a transaction for a given merchant.
                    Based on how previous transactions for this merchant were categorized.

                    Returns top suggestions ranked by confidence (% of past uses).
                    Useful for building auto-categorization or quickly picking the right account.

                    Example: suggest_category_for_merchant(merchant="ZOMATO")
                    → Food & Dining: 90% confidence (9/10 past transactions)
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public MerchantCategorySuggestionDto suggestCategoryForMerchant(
            @McpArg(name = "merchant", description = "Merchant name to look up (case-insensitive)", required = true) String merchant
    ) {
        return reportingService.suggestCategoryForMerchant(merchant);
    }

    @McpTool(
            name = "get_financial_health_score",
            description = """
                    Computes a 0–100 financial health score with letter grade (A/B/C/D/F).
                    Based on the last 3 months of data.

                    Three components (each scored independently):
                      emergencyFund  — months of expenses covered by cash+banks (max 40 pts)
                                       CRITICAL(<1m) | LOW(1-3m) | ADEQUATE(3-6m) | STRONG(>6m)
                      savingsRate    — (income − expenses) ÷ income % (max 35 pts)
                                       LOW(<10%) | MODERATE(10-20%) | GOOD(20-30%) | EXCELLENT(>30%)
                      debtBurden     — monthly EMIs ÷ monthly income % (max 25 pts)
                                       HEALTHY(<35%) | MODERATE(35-50%) | HIGH(>50%)

                    Also returns personalised recommendations.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public FinancialHealthDto getFinancialHealthScore() {
        return reportingService.getFinancialHealthScore();
    }
}
