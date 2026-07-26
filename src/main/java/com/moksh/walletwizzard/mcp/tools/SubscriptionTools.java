package com.moksh.walletwizzard.mcp.tools;

import tools.jackson.databind.ObjectMapper;
import com.moksh.walletwizzard.dto.CreateSubscriptionRequest;
import com.moksh.walletwizzard.entity.Subscription;
import com.moksh.walletwizzard.enums.BillingCycle;
import com.moksh.walletwizzard.enums.RecurringSide;
import com.moksh.walletwizzard.enums.ScheduleType;
import com.moksh.walletwizzard.enums.SubscriptionStatus;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.mcp.dto.SubView;
import com.moksh.walletwizzard.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionTools {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "add_recurring_transaction",
            description = """
                    Creates a recurring transaction — outgoing (DEBIT) like Netflix, rent, EMI, or incoming (CREDIT) like salary, dividend, rental income.

                    side: DEBIT (money goes out) | CREDIT (money comes in)
                    billingCycle: WEEKLY | MONTHLY | QUARTERLY | YEARLY
                    scheduleType: FIXED_DAY (same date each cycle) | LAST_DAY (last calendar day) | LAST_WEEKDAY (last Mon–Fri, e.g. salary)
                    paymentAccountId: bank account or credit card (pays for DEBIT, receives for CREDIT)
                    categoryAccountId: expense account for DEBIT side, income account for CREDIT side
                    nextBillingDate: the next date this transaction is due
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addRecurringTransaction(
            @McpArg(name = "name", description = "Name e.g. 'Netflix', 'Salary', 'AWS'", required = true) String name,
            @McpArg(name = "amount", description = "Amount e.g. '649.00'", required = true) String amount,
            @McpArg(name = "billingCycle", description = "WEEKLY | MONTHLY | QUARTERLY | YEARLY", required = true) String billingCycle,
            @McpArg(name = "side", description = "DEBIT (outgoing) | CREDIT (incoming)", required = true) String side,
            @McpArg(name = "scheduleType", description = "FIXED_DAY | LAST_DAY | LAST_WEEKDAY", required = false) String scheduleType,
            @McpArg(name = "paymentAccountId", description = "UUID of bank account / credit card", required = true) String paymentAccountId,
            @McpArg(name = "categoryAccountId", description = "UUID of expense account (DEBIT) or income account (CREDIT)", required = true) String categoryAccountId,
            @McpArg(name = "nextBillingDate", description = "Next due date YYYY-MM-DD (optional)", required = false) String nextBillingDate,
            @McpArg(name = "notes", description = "Optional notes", required = false) String notes
    ) {
        ScheduleType parsedSchedule = scheduleType != null && !scheduleType.isBlank()
                ? McpInputs.requireEnum(scheduleType, "scheduleType", ScheduleType.class)
                : ScheduleType.FIXED_DAY;

        Subscription sub = subscriptionService.createSubscription(new CreateSubscriptionRequest(
                name,
                McpInputs.requireAmount(amount, "amount"),
                McpInputs.requireEnum(billingCycle, "billingCycle", BillingCycle.class),
                McpInputs.parseDate(nextBillingDate, "nextBillingDate"),
                McpInputs.requireEnum(side, "side", RecurringSide.class),
                parsedSchedule,
                McpInputs.requireUuid(paymentAccountId, "paymentAccountId"),
                McpInputs.requireUuid(categoryAccountId, "categoryAccountId"),
                McpInputs.blankToNull(notes)
        ));
        return "Recurring transaction created: id=" + sub.getId() + ", name=" + sub.getName()
                + ", side=" + sub.getSide() + ", amount=" + sub.getAmount()
                + " " + sub.getBillingCycle() + " (" + sub.getScheduleType() + ")";
    }

    @McpTool(
            name = "record_recurring_payment",
            description = """
                    Records one cycle of a recurring transaction and advances the next due date.
                    DEBIT: DEBIT expense account, CREDIT payment account.
                    CREDIT: DEBIT payment account, CREDIT income account.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String recordRecurringPayment(
            @McpArg(name = "recurringId", description = "UUID of the recurring transaction", required = true) String recurringId,
            @McpArg(name = "paymentDate", description = "Date of transaction YYYY-MM-DD (defaults to today)", required = false) String paymentDate
    ) {
        LocalDate date = McpInputs.parseDate(paymentDate, "paymentDate");
        LocalDate effective = date != null ? date : LocalDate.now();
        subscriptionService.recordPayment(McpInputs.requireUuid(recurringId, "recurringId"), effective);
        return "Recurring payment recorded for " + recurringId + " on " + effective;
    }

    @McpTool(
            name = "get_upcoming_bills",
            description = "Lists active recurring transactions due within the next N days, ordered by due date.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getUpcomingBills(
            @McpArg(name = "days", description = "Look-ahead window in days (default 30)", required = false) Integer days
    ) {
        int lookAhead = days != null ? days : 30;
        List<Subscription> subs = subscriptionService.getUpcomingBills(lookAhead);
        return objectMapper.writeValueAsString(subs.stream()
                .map(this::toView)
                .toList());
    }

    @McpTool(
            name = "list_recurring_transactions",
            description = "Lists all recurring transactions. Filter by status: ACTIVE | PAUSED | CANCELLED.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String listRecurringTransactions(
            @McpArg(name = "status", description = "ACTIVE | PAUSED | CANCELLED (omit for all)", required = false) String status
    ) {
        List<Subscription> subs = subscriptionService.listSubscriptions(
                McpInputs.parseEnum(status, "status", SubscriptionStatus.class)
        );
        return objectMapper.writeValueAsString(subs.stream()
                .map(this::toView)
                .toList());
    }

    @McpTool(
            name = "update_recurring_transaction_status",
            description = "Changes a recurring transaction's status to ACTIVE, PAUSED, or CANCELLED.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, idempotentHint = true)
    )
    public String updateRecurringTransactionStatus(
            @McpArg(name = "recurringId", description = "UUID of the recurring transaction", required = true) String recurringId,
            @McpArg(name = "status", description = "ACTIVE | PAUSED | CANCELLED", required = true) String status
    ) {
        subscriptionService.updateStatus(
                McpInputs.requireUuid(recurringId, "recurringId"),
                McpInputs.requireEnum(status, "status", SubscriptionStatus.class)
        );
        return "Recurring transaction " + recurringId + " status updated to " + status;
    }

    private SubView toView(Subscription s) {
        return new SubView(
                s.getId().toString(),
                s.getName(),
                s.getAmount().toPlainString(),
                s.getBillingCycle().name(),
                s.getSide().name(),
                s.getScheduleType().name(),
                s.getNextBillingDate() != null ? s.getNextBillingDate().toString() : null
        );
    }
}
