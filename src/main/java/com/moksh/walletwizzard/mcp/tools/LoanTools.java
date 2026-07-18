package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.CreateLoanRequest;
import com.moksh.walletwizzard.dto.LoanSummary;
import com.moksh.walletwizzard.dto.RecordLoanPaymentRequest;
import com.moksh.walletwizzard.enums.LoanDirection;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoanTools {

    private final LoanService loanService;

    @McpTool(
            name = "add_loan",
            description = """
                    Records a loan (structured, with EMI schedule) and creates its tracking account.

                    direction = TAKEN: you borrowed money (home loan, car loan, personal loan)
                    direction = GIVEN: you lent money as a formal loan with interest

                    The loan account tracks the outstanding principal automatically.
                    interestRate is a decimal: 0.0875 = 8.75% per annum.
                    bankAccountId: where the loan proceeds were deposited (TAKEN) or withdrawn (GIVEN).
                    interestAccountId: expense account for interest (TAKEN) or income account (GIVEN).
                      Leave null to use the first system Expense/Income account automatically.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addLoan(
            @McpArg(name = "name", description = "Loan name e.g. 'Home Loan - HDFC'", required = true) String name,
            @McpArg(name = "direction", description = "TAKEN (you borrowed) or GIVEN (you lent)", required = true) String direction,
            @McpArg(name = "principal", description = "Total loan amount e.g. '500000.00'", required = true) String principal,
            @McpArg(name = "interestRate", description = "Annual rate as decimal e.g. 0.0875 for 8.75% (null for zero-interest)", required = false) String interestRate,
            @McpArg(name = "emiAmount", description = "Monthly EMI amount e.g. '15000.00' (optional)", required = false) String emiAmount,
            @McpArg(name = "startDate", description = "Loan start date YYYY-MM-DD", required = false) String startDate,
            @McpArg(name = "endDate", description = "Loan end date YYYY-MM-DD (optional)", required = false) String endDate,
            @McpArg(name = "bankAccountId", description = "UUID of account where loan proceeds landed", required = true) String bankAccountId,
            @McpArg(name = "interestAccountId", description = "UUID of interest expense/income account (optional, auto-resolved if omitted)", required = false) String interestAccountId,
            @McpArg(name = "notes", description = "Optional notes", required = false) String notes
    ) {
        var loan = loanService.createLoan(new CreateLoanRequest(
                name,
                McpInputs.requireEnum(direction, "direction", LoanDirection.class),
                McpInputs.requireAmount(principal, "principal"),
                McpInputs.parseAmount(interestRate, "interestRate"),
                McpInputs.parseAmount(emiAmount, "emiAmount"),
                McpInputs.parseDate(startDate, "startDate"),
                McpInputs.parseDate(endDate, "endDate"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseUuid(interestAccountId, "interestAccountId"),
                McpInputs.blankToNull(notes)
        ));
        return "Loan created: id=" + loan.getId() + ", name=" + loan.getName()
                + ", principal=" + loan.getPrincipal() + ", direction=" + loan.getDirection();
    }

    @McpTool(
            name = "record_loan_payment",
            description = """
                    Records one EMI payment for a loan.

                    The payment is split into principal + interest portions and recorded as a
                    multi-leg journal entry. The loan account balance (outstanding principal)
                    automatically decreases by the principal portion.

                    principalPortion + interestPortion = total payment amount.
                    bankAccountId: the account debited (TAKEN) or credited (GIVEN) for the payment.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String recordLoanPayment(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId,
            @McpArg(name = "principalPortion", description = "Amount applied to principal e.g. '12000.00'", required = true) String principalPortion,
            @McpArg(name = "interestPortion", description = "Interest portion e.g. '3000.00' (use '0' if zero-interest)", required = true) String interestPortion,
            @McpArg(name = "date", description = "Payment date YYYY-MM-DD", required = true) String date,
            @McpArg(name = "bankAccountId", description = "UUID of account used to make/receive the payment", required = true) String bankAccountId
    ) {
        loanService.recordPayment(new RecordLoanPaymentRequest(
                McpInputs.requireUuid(loanId, "loanId"),
                McpInputs.requireAmount(principalPortion, "principalPortion"),
                McpInputs.requireAmount(interestPortion, "interestPortion"),
                McpInputs.requireDate(date, "date"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId")
        ));
        return "Loan payment recorded for loan " + loanId
                + ". Principal: " + principalPortion + ", Interest: " + interestPortion;
    }

    @McpTool(
            name = "list_loans",
            description = "Lists all loans with their current outstanding balances.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<LoanSummary> listLoans() {
        return loanService.listLoans();
    }

    @McpTool(
            name = "get_loan_summary",
            description = "Returns the current state of a loan including outstanding balance.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public LoanSummary getLoanSummary(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId
    ) {
        return loanService.getLoanSummary(McpInputs.requireUuid(loanId, "loanId"));
    }
}
