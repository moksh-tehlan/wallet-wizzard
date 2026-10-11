package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.reporting.dto.BudgetStatusDto;
import com.moksh.walletwizzard.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BudgetTools {

    private final BudgetService budgetService;

    @McpTool(
            name = "set_budget",
            description = """
                    Sets or updates a monthly spending budget for an expense account.
                    If a budget already exists for this account, it is updated.

                    Example: set_budget(accountId=<Food & Dining id>, amount=8000)
                    → Alerts via get_budget_status when you hit 80% or 100%.

                    Only EXPENSE accounts should have budgets set.
                    Use list_accounts to find the accountId.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String setBudget(
            @McpArg(name = "accountId", description = "UUID of the expense account to budget", required = true) String accountId,
            @McpArg(name = "amount", description = "Monthly budget limit in rupees", required = true) String amount
    ) {
        var budget = budgetService.setBudget(
                McpInputs.requireUuid(accountId, "accountId"),
                McpInputs.requireAmount(amount, "amount"));
        return "Budget set: id=" + budget.getId() + ", account=" + budget.getAccount().getId()
                + ", amount=₹" + budget.getAmount() + "/month";
    }

    @McpTool(
            name = "get_budget_status",
            description = """
                    Shows spending vs budget for every budgeted category in a given month.

                    Each result includes:
                      budgetAmount  — your monthly limit
                      spentAmount   — actual spend so far
                      remainingAmount — how much is left
                      percentageUsed — spent ÷ budget × 100
                      status        — ON_TRACK (< 80%) | WARNING (80–99%) | EXCEEDED (≥ 100%)

                    Omit year/month to default to the current month.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<BudgetStatusDto> getBudgetStatus(
            @McpArg(name = "year", description = "4-digit year, default current", required = false) Integer year,
            @McpArg(name = "month", description = "Month 1-12, default current", required = false) Integer month
    ) {
        int y = year != null ? year : LocalDate.now().getYear();
        int m = month != null ? month : LocalDate.now().getMonthValue();
        return budgetService.getBudgetStatus(y, m);
    }

    @McpTool(
            name = "delete_budget",
            description = "Removes a monthly budget for a category. Use get_budget_status to find the budgetId.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true)
    )
    public String deleteBudget(
            @McpArg(name = "budgetId", description = "UUID of the budget to delete", required = true) String budgetId
    ) {
        budgetService.deleteBudget(McpInputs.requireUuid(budgetId, "budgetId"));
        return "Budget " + budgetId + " deleted.";
    }
}
