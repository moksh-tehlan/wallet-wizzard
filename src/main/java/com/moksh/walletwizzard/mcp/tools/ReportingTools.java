package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.reporting.ReportingService;
import com.moksh.walletwizzard.reporting.dto.CashFlowDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyReportDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyTrendDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.SpendingAverageDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByCategoryDto;
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
}
