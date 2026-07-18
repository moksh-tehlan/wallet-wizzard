package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.CreateDebtRequest;
import com.moksh.walletwizzard.dto.DebtSummary;
import com.moksh.walletwizzard.enums.DebtDirection;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.DebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DebtTools {

    private final DebtService debtService;

    @McpTool(
            name = "add_debt",
            description = """
                    Records a debt or lending transaction and creates a dedicated tracking account.

                    direction = LENT:     you lent money to someone (creates a Receivable account)
                    direction = BORROWED: you borrowed money from someone (creates a Payable account)

                    The outstanding balance is automatically tracked via journal entries.
                    Balance = 0 means the debt is fully settled — no manual status update needed.

                    The bankAccountId is the account the money moved through
                    (e.g. your Checking account when you sent a bank transfer).
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addDebt(
            @McpArg(name = "personId", description = "UUID of the person involved", required = true)
            String personId,
            @McpArg(name = "direction", description = "LENT (you gave money) or BORROWED (you received money)", required = true)
            String direction,
            @McpArg(name = "amount", description = "Amount e.g. '5000.00'", required = true)
            String amount,
            @McpArg(name = "bankAccountId", description = "UUID of your bank/wallet account that the money passed through", required = true)
            String bankAccountId,
            @McpArg(name = "date", description = "Date YYYY-MM-DD (defaults to today)", required = false)
            String date,
            @McpArg(name = "dueDate", description = "Repayment deadline YYYY-MM-DD (optional)", required = false)
            String dueDate,
            @McpArg(name = "description", description = "Optional note about this debt", required = false)
            String description
    ) {
        var record = debtService.createDebt(new CreateDebtRequest(
                McpInputs.requireUuid(personId, "personId"),
                McpInputs.requireEnum(direction, "direction", DebtDirection.class),
                McpInputs.requireAmount(amount, "amount"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseDate(date, "date"),
                McpInputs.parseDate(dueDate, "dueDate"),
                McpInputs.blankToNull(description)
        ));
        return "Debt recorded: id=" + record.getId()
                + ", direction=" + record.getDirection()
                + ", amount=" + record.getOriginalAmount();
    }

    @McpTool(
            name = "list_debts",
            description = """
                    Lists all debts and lendings with their current outstanding balances.
                    Filter by direction (LENT/BORROWED) or by person.
                    outstandingBalance = 0 means the debt is fully settled.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<DebtSummary> listDebts(
            @McpArg(name = "direction", description = "LENT | BORROWED (omit for all)", required = false)
            String direction,
            @McpArg(name = "personId", description = "Filter by person UUID (optional)", required = false)
            String personId
    ) {
        return debtService.listDebts(
                McpInputs.parseEnum(direction, "direction", DebtDirection.class),
                McpInputs.parseUuid(personId, "personId")
        );
    }

    @McpTool(
            name = "get_debt_summary",
            description = "Returns the current state of a single debt including its outstanding balance.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public DebtSummary getDebtSummary(
            @McpArg(name = "debtId", description = "UUID of the debt record", required = true)
            String debtId
    ) {
        return debtService.getDebtSummary(McpInputs.requireUuid(debtId, "debtId"));
    }
}
