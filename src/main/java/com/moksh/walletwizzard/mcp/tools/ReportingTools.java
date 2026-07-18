package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.reporting.ReportingService;
import com.moksh.walletwizzard.reporting.dto.CashFlowDto;
import com.moksh.walletwizzard.reporting.dto.MonthlyReportDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.SpendingByCategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

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
                    Returns total assets, total liabilities, and net worth (assets − liabilities).
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
}
