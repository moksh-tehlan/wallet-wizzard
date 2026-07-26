package com.moksh.walletwizzard.mcp.tools;

import tools.jackson.databind.ObjectMapper;
import com.moksh.walletwizzard.dto.CreateInvestmentRequest;
import com.moksh.walletwizzard.entity.Investment;
import com.moksh.walletwizzard.enums.InvestmentType;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.InvestmentService;
import com.moksh.walletwizzard.service.MfNavService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InvestmentTools {

    private final InvestmentService investmentService;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "search_mutual_fund",
            description = """
                    Searches for mutual fund schemes by name using mfapi.in (covers all AMFI schemes).
                    Returns schemeCode and schemeName. Use schemeCode in add_investment.
                    Example: search 'Axis Bluechip' or 'HDFC Mid Cap'.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String searchMutualFund(
            @McpArg(name = "query", description = "Fund name or partial name to search", required = true) String query
    ) {
        List<MfNavService.SchemeResult> results = investmentService.searchMutualFunds(query);
        if (results.isEmpty()) return "No schemes found for '" + query + "'.";
        return objectMapper.writeValueAsString(results.stream()
                .limit(10)
                .map(r -> Map.of("schemeCode", String.valueOf(r.schemeCode()), "schemeName", r.schemeName()))
                .toList());
    }

    @McpTool(
            name = "add_investment",
            description = """
                    Adds a new investment. Supported types:

                    MUTUAL_FUND — requires schemeCode (from search_mutual_fund) and units.
                      Live NAV fetched from mfapi.in automatically.

                    EPF — Employee Provident Fund. Use current EPF balance as amount.
                      interestRate defaults to 0.0825 (8.25%). bankAccountId optional (existing balance).
                      monthlyContribution: set if you want to track ongoing deductions.

                    FD  — Fixed Deposit. Requires interestRate (e.g. 0.07 = 7%), maturityDate.
                      bankAccountId is the account the FD was funded from.

                    RD  — Recurring Deposit. Requires interestRate, monthlyContribution, maturityDate.
                      amount = first installment amount.

                    interestRate: decimal — 0.07 = 7%, 0.0825 = 8.25%
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addInvestment(
            @McpArg(name = "name", description = "Investment name e.g. 'Axis Bluechip Direct Growth', 'HDFC FD'", required = true) String name,
            @McpArg(name = "type", description = "MUTUAL_FUND | EPF | FD | RD", required = true) String type,
            @McpArg(name = "amount", description = "Amount invested / current balance", required = true) String amount,
            @McpArg(name = "schemeCode", description = "MF only: scheme code from search_mutual_fund", required = false) String schemeCode,
            @McpArg(name = "units", description = "MF only: units held", required = false) String units,
            @McpArg(name = "interestRate", description = "EPF/FD/RD: annual rate as decimal (0.07 = 7%)", required = false) String interestRate,
            @McpArg(name = "monthlyContribution", description = "EPF/RD: monthly installment amount", required = false) String monthlyContribution,
            @McpArg(name = "startDate", description = "Start date YYYY-MM-DD (defaults to today)", required = false) String startDate,
            @McpArg(name = "maturityDate", description = "FD/RD: maturity date YYYY-MM-DD", required = false) String maturityDate,
            @McpArg(name = "bankAccountId", description = "Bank account to debit (null for existing EPF balance)", required = false) String bankAccountId,
            @McpArg(name = "notes", description = "Optional notes", required = false) String notes
    ) {
        InvestmentType invType = McpInputs.requireEnum(type, "type", InvestmentType.class);

        Investment inv = investmentService.addInvestment(new CreateInvestmentRequest(
                name,
                invType,
                McpInputs.requireAmount(amount, "amount"),
                McpInputs.blankToNull(schemeCode),
                units != null && !units.isBlank() ? new BigDecimal(units) : null,
                interestRate != null && !interestRate.isBlank() ? new BigDecimal(interestRate) : null,
                monthlyContribution != null && !monthlyContribution.isBlank() ? new BigDecimal(monthlyContribution) : null,
                McpInputs.parseDate(startDate, "startDate"),
                McpInputs.parseDate(maturityDate, "maturityDate"),
                bankAccountId != null && !bankAccountId.isBlank() ? McpInputs.requireUuid(bankAccountId, "bankAccountId") : null,
                McpInputs.blankToNull(notes)
        ));

        return "Investment added: id=" + inv.getId() + ", name=" + inv.getName()
                + ", invested=" + inv.getInvestedAmount()
                + ", currentValue=" + inv.getCurrentValue();
    }

    @McpTool(
            name = "record_investment_contribution",
            description = """
                    Records a new contribution to an existing investment (SIP for MF, monthly RD installment, EPF deduction).
                    Updates invested amount and recomputes current value.
                    For MF SIP: also provide unitsAdded (units purchased this time).
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String recordInvestmentContribution(
            @McpArg(name = "investmentId", description = "UUID of the investment", required = true) String investmentId,
            @McpArg(name = "amount", description = "Amount contributed this time", required = true) String amount,
            @McpArg(name = "unitsAdded", description = "MF only: units purchased this contribution", required = false) String unitsAdded,
            @McpArg(name = "bankAccountId", description = "Bank account to debit (optional for EPF payroll)", required = false) String bankAccountId
    ) {
        Investment inv = investmentService.recordContribution(
                McpInputs.requireUuid(investmentId, "investmentId"),
                McpInputs.requireAmount(amount, "amount"),
                unitsAdded != null && !unitsAdded.isBlank() ? new BigDecimal(unitsAdded) : null,
                bankAccountId != null && !bankAccountId.isBlank() ? McpInputs.requireUuid(bankAccountId, "bankAccountId") : null
        );
        return "Contribution recorded. Total invested: " + inv.getInvestedAmount()
                + ", current value: " + inv.getCurrentValue();
    }

    @McpTool(
            name = "refresh_investment_values",
            description = """
                    Refreshes current values for all investments:
                    - MUTUAL_FUND: fetches live NAV from mfapi.in and recomputes units × NAV
                    - FD: recomputes using quarterly compounding from start date
                    - RD: recomputes using simple interest approximation
                    - EPF: recomputes using compound interest at stored rate
                    Call this before get_portfolio for fresh numbers.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, idempotentHint = true)
    )
    public String refreshInvestmentValues() {
        List<Investment> updated = investmentService.refreshAll();
        BigDecimal totalValue = updated.stream()
                .map(Investment::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return "Refreshed " + updated.size() + " investments. Total portfolio value: ₹" + totalValue.toPlainString();
    }

    @McpTool(
            name = "get_portfolio",
            description = """
                    Returns all investments with invested amount, current value, and gain/loss.
                    Optionally filter by type: MUTUAL_FUND | EPF | FD | RD.
                    Call refresh_investment_values first for live MF NAVs.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getPortfolio(
            @McpArg(name = "type", description = "MUTUAL_FUND | EPF | FD | RD (omit for all)", required = false) String type
    ) {
        InvestmentType filter = McpInputs.parseEnum(type, "type", InvestmentType.class);
        List<Investment> investments = investmentService.getPortfolio(filter);

        if (investments.isEmpty()) return "No investments found.";

        BigDecimal totalInvested = investments.stream().map(Investment::getInvestedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = investments.stream().map(Investment::getCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGain = totalValue.subtract(totalInvested);

        List<Map<String, String>> rows = investments.stream().map(inv -> {
            BigDecimal gain = inv.getCurrentValue().subtract(inv.getInvestedAmount());
            BigDecimal gainPct = inv.getInvestedAmount().compareTo(BigDecimal.ZERO) > 0
                    ? gain.divide(inv.getInvestedAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            return Map.of(
                    "id", inv.getId().toString(),
                    "name", inv.getName(),
                    "type", inv.getType().name(),
                    "investedAmount", inv.getInvestedAmount().toPlainString(),
                    "currentValue", inv.getCurrentValue().toPlainString(),
                    "gain", gain.toPlainString(),
                    "gainPct", gainPct.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%",
                    "lastRefreshed", inv.getLastRefreshedAt() != null ? inv.getLastRefreshedAt().toString() : "never"
            );
        }).toList();

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("summary", Map.of(
                "totalInvested", totalInvested.toPlainString(),
                "totalValue", totalValue.toPlainString(),
                "totalGain", totalGain.toPlainString()));
        result.put("investments", rows);
        return objectMapper.writeValueAsString(result);
    }

    @McpTool(
            name = "close_investment",
            description = """
                    Closes/matures an investment (FD maturity, MF redemption, etc.).
                    Credits the current account balance back to the specified bank account.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String closeInvestment(
            @McpArg(name = "investmentId", description = "UUID of the investment to close", required = true) String investmentId,
            @McpArg(name = "bankAccountId", description = "Bank account to receive the proceeds", required = true) String bankAccountId,
            @McpArg(name = "date", description = "Closure date YYYY-MM-DD (defaults to today)", required = false) String date
    ) {
        Investment inv = investmentService.closeInvestment(
                McpInputs.requireUuid(investmentId, "investmentId"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseDate(date, "date")
        );
        return "Investment '" + inv.getName() + "' closed. Proceeds transferred to bank account.";
    }
}
