package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.InstallmentDto;
import com.moksh.walletwizzard.dto.LoanShareSummaryDto;
import com.moksh.walletwizzard.dto.PersonBalanceDto;
import com.moksh.walletwizzard.dto.UpcomingPaymentDto;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.LoanScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LoanScheduleTools {

    private final LoanScheduleService loanScheduleService;

    @McpTool(
            name = "generate_loan_schedule",
            description = """
                    Generates an amortization schedule (list of monthly installments) for a loan.
                    Uses the loan's interestRate and principal to calculate EMI automatically.
                    If the loan already has emiAmount set, that is used directly.
                    firstDueDate defaults to loanStartDate + 1 month (or today + 1 month if no startDate).

                    Call get_loan_schedule afterwards to see the full installment list.
                    Only call once per loan — throws an error if a schedule already exists.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String generateLoanSchedule(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId,
            @McpArg(name = "numberOfInstallments", description = "Total number of monthly EMIs e.g. 24 for 2-year loan", required = true) int numberOfInstallments,
            @McpArg(name = "firstDueDate", description = "Date of first installment YYYY-MM-DD (optional, defaults to startDate + 1 month)", required = false) String firstDueDate
    ) {
        List<InstallmentDto> schedule = loanScheduleService.generateSchedule(
                McpInputs.requireUuid(loanId, "loanId"),
                numberOfInstallments,
                McpInputs.parseDate(firstDueDate, "firstDueDate")
        );
        return "Schedule generated: " + schedule.size() + " installments for loan " + loanId
                + ". First due: " + schedule.get(0).dueDate()
                + ", EMI: " + schedule.get(0).totalAmount()
                + ". Use get_loan_schedule to view full schedule.";
    }

    @McpTool(
            name = "import_loan_schedule",
            description = """
                    Manually imports a loan schedule when you have the exact installment amounts
                    (e.g., from a bank statement or PDF amortization table).
                    Use when the loan has non-standard EMIs or you want to match the bank's schedule exactly.
                    Each item needs: dueDate, principalAmount, interestAmount (optional, default 0).
                    Only call once per loan — throws if a schedule already exists.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String importLoanSchedule(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId,
            @McpArg(name = "installments", description = "List of installments: [{dueDate, principalAmount, interestAmount}]", required = true)
            List<InstallmentInputDto> installments
    ) {
        List<LoanScheduleService.InstallmentInput> inputs = installments.stream()
                .map(i -> new LoanScheduleService.InstallmentInput(
                        McpInputs.requireDate(i.dueDate(), "dueDate"),
                        McpInputs.requireAmount(i.principalAmount(), "principalAmount"),
                        McpInputs.parseAmount(i.interestAmount(), "interestAmount")
                ))
                .toList();

        List<InstallmentDto> saved = loanScheduleService.importSchedule(
                McpInputs.requireUuid(loanId, "loanId"), inputs);
        return "Imported " + saved.size() + " installments for loan " + loanId + ".";
    }

    @McpTool(
            name = "get_loan_schedule",
            description = "Returns the full installment schedule for a loan with status, due dates, and amounts.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<InstallmentDto> getLoanSchedule(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId
    ) {
        return loanScheduleService.getSchedule(McpInputs.requireUuid(loanId, "loanId"));
    }

    @McpTool(
            name = "pay_installment",
            description = """
                    Records payment of a specific loan installment.
                    Creates a journal entry (INSTALLMENT_PAYMENT) that debits the loan account
                    (principal) and interest account, crediting the bank account.
                    Marks the installment as PAID.
                    Use get_loan_schedule to find the installmentId to pay.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String payInstallment(
            @McpArg(name = "installmentId", description = "UUID of the installment to pay", required = true) String installmentId,
            @McpArg(name = "bankAccountId", description = "UUID of bank account used for payment", required = true) String bankAccountId,
            @McpArg(name = "date", description = "Payment date YYYY-MM-DD (defaults to today)", required = false) String date
    ) {
        InstallmentDto result = loanScheduleService.payInstallment(
                McpInputs.requireUuid(installmentId, "installmentId"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseDate(date, "date")
        );
        return "Installment #" + result.installmentNo() + " paid on " + result.paidDate()
                + ". Principal: " + result.principalAmount() + ", Interest: " + result.interestAmount()
                + ", Total: " + result.totalAmount();
    }

    @McpTool(
            name = "add_loan_participant",
            description = """
                    Adds a co-borrower or co-lender to a loan with their share percentage.
                    For a TAKEN loan: the participant shares the repayment burden with you.
                    For a GIVEN loan: the participant co-funded the loan.
                    sharePercent: 0–100. Multiple participants can be added; total can be up to 100%.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addLoanParticipant(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId,
            @McpArg(name = "personId", description = "UUID of the person (co-borrower)", required = true) String personId,
            @McpArg(name = "sharePercent", description = "Their share as a percentage e.g. 50 for 50%", required = true) String sharePercent,
            @McpArg(name = "notes", description = "Optional notes", required = false) String notes
    ) {
        BigDecimal share = McpInputs.requireAmount(sharePercent, "sharePercent");
        if (share.compareTo(BigDecimal.ZERO) <= 0 || share.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new com.moksh.walletwizzard.exception.InvalidInputException(
                    "sharePercent must be between 0 and 100, got: " + share);
        }
        var participant = loanScheduleService.addParticipant(
                McpInputs.requireUuid(loanId, "loanId"),
                McpInputs.requireUuid(personId, "personId"),
                share,
                McpInputs.blankToNull(notes)
        );
        return "Participant added: id=" + participant.getId()
                + ", person=" + participant.getPerson().getName()
                + ", share=" + participant.getSharePercent() + "%";
    }

    @McpTool(
            name = "get_loan_share_summary",
            description = """
                    Shows how much each participant owes (or is owed) for a shared loan.
                    Breaks down by: paidByUser (installments already paid), currentlyDue, and scheduledFuture.
                    Use after adding participants to see the sharing breakdown.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public LoanShareSummaryDto getLoanShareSummary(
            @McpArg(name = "loanId", description = "UUID of the loan", required = true) String loanId
    ) {
        return loanScheduleService.getShareSummary(McpInputs.requireUuid(loanId, "loanId"));
    }

    @McpTool(
            name = "get_upcoming_loan_payments",
            description = """
                    Lists loan installments due within the next N days.
                    Shows both DUE (already past due) and SCHEDULED (upcoming) installments.
                    Use alongside get_upcoming_bills for a complete payment picture.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<UpcomingPaymentDto> getUpcomingLoanPayments(
            @McpArg(name = "days", description = "Look-ahead window in days (default 30)", required = false) Integer days
    ) {
        return loanScheduleService.getUpcomingPayments(days != null ? days : 30);
    }

    @McpTool(
            name = "get_person_balance",
            description = """
                    Returns the full financial balance with a person — combining direct debts and loan shares.

                    directDebts: debts recorded via add_debt (e.g. you lent them cash directly).
                    loanParticipations: their share of joint loans (paidByUser = you've paid this for them,
                      currentlyDue = installments overdue, scheduledFuture = upcoming EMIs).
                    netBalance: positive = they owe you, negative = you owe them.

                    Use this to answer: 'How much does Himanshu owe me?' or 'What do I owe Riya?'
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public PersonBalanceDto getPersonBalance(
            @McpArg(name = "personId", description = "UUID of the person", required = true) String personId
    ) {
        return loanScheduleService.getPersonBalance(McpInputs.requireUuid(personId, "personId"));
    }

    // ── Input DTO for import ─────────────────────────────────────────────────

    public record InstallmentInputDto(
            String dueDate,
            String principalAmount,
            String interestAmount
    ) {}
}
