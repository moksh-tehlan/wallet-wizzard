package com.moksh.walletwizzard.mcp.tools;

import tools.jackson.databind.ObjectMapper;
import com.moksh.walletwizzard.dto.CreateSubscriptionRequest;
import com.moksh.walletwizzard.entity.Subscription;
import com.moksh.walletwizzard.enums.BillingCycle;
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
            name = "add_subscription",
            description = """
                    Creates a recurring subscription (Netflix, Spotify, AWS, gym membership, etc.).

                    billingCycle: WEEKLY | MONTHLY | QUARTERLY | YEARLY
                    paymentAccountId: bank account or credit card that pays
                    expenseAccountId: expense category (e.g. the 'Subscriptions' or 'Entertainment' account)
                    nextBillingDate: the next date this subscription will be charged
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addSubscription(
            @McpArg(name = "name", description = "Subscription name e.g. 'Netflix', 'AWS'", required = true) String name,
            @McpArg(name = "amount", description = "Billing amount e.g. '649.00'", required = true) String amount,
            @McpArg(name = "billingCycle", description = "WEEKLY | MONTHLY | QUARTERLY | YEARLY", required = true) String billingCycle,
            @McpArg(name = "paymentAccountId", description = "UUID of account/card that pays for this", required = true) String paymentAccountId,
            @McpArg(name = "expenseAccountId", description = "UUID of expense account for categorisation", required = true) String expenseAccountId,
            @McpArg(name = "nextBillingDate", description = "Next charge date YYYY-MM-DD (optional)", required = false) String nextBillingDate,
            @McpArg(name = "notes", description = "Optional notes", required = false) String notes
    ) {
        var sub = subscriptionService.createSubscription(new CreateSubscriptionRequest(
                name,
                McpInputs.requireAmount(amount, "amount"),
                McpInputs.requireEnum(billingCycle, "billingCycle", BillingCycle.class),
                McpInputs.parseDate(nextBillingDate, "nextBillingDate"),
                McpInputs.requireUuid(paymentAccountId, "paymentAccountId"),
                McpInputs.requireUuid(expenseAccountId, "expenseAccountId"),
                McpInputs.blankToNull(notes)
        ));
        return "Subscription created: id=" + sub.getId() + ", name=" + sub.getName()
                + ", amount=" + sub.getAmount() + " " + sub.getBillingCycle();
    }

    @McpTool(
            name = "record_subscription_payment",
            description = """
                    Records one billing cycle payment for a subscription and advances the next billing date.
                    Creates a journal entry: DEBIT expense account, CREDIT payment account.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String recordSubscriptionPayment(
            @McpArg(name = "subscriptionId", description = "UUID of the subscription", required = true) String subscriptionId,
            @McpArg(name = "paymentDate", description = "Date charged YYYY-MM-DD (defaults to today)", required = false) String paymentDate
    ) {
        LocalDate date = McpInputs.parseDate(paymentDate, "paymentDate");
        subscriptionService.recordPayment(
                McpInputs.requireUuid(subscriptionId, "subscriptionId"),
                date != null ? date : LocalDate.now()
        );
        return "Subscription payment recorded for " + subscriptionId + " on " + (date != null ? date : LocalDate.now());
    }

    @McpTool(
            name = "get_upcoming_bills",
            description = "Lists active subscriptions due within the next N days, ordered by due date.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getUpcomingBills(
            @McpArg(name = "days", description = "Look-ahead window in days (default 30)", required = false) Integer days
    ) {
        int lookAhead = days != null ? days : 30;
        List<Subscription> subs = subscriptionService.getUpcomingBills(lookAhead);
        return objectMapper.writeValueAsString(subs.stream()
                .map(s -> new SubView(
                        s.getId().toString(), s.getName(),
                        s.getAmount().toPlainString(), s.getBillingCycle().name(),
                        s.getNextBillingDate() != null ? s.getNextBillingDate().toString() : null
                ))
                .toList());
    }

    @McpTool(
            name = "list_subscriptions",
            description = "Lists all subscriptions. Filter by status: ACTIVE | PAUSED | CANCELLED.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String listSubscriptions(
            @McpArg(name = "status", description = "ACTIVE | PAUSED | CANCELLED (omit for all)", required = false) String status
    ) {
        List<Subscription> subs = subscriptionService.listSubscriptions(
                McpInputs.parseEnum(status, "status", SubscriptionStatus.class)
        );
        return objectMapper.writeValueAsString(subs.stream()
                .map(s -> new SubView(
                        s.getId().toString(), s.getName(),
                        s.getAmount().toPlainString(), s.getBillingCycle().name(),
                        s.getNextBillingDate() != null ? s.getNextBillingDate().toString() : null
                ))
                .toList());
    }

    @McpTool(
            name = "update_subscription_status",
            description = "Changes a subscription's status to ACTIVE, PAUSED, or CANCELLED.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, idempotentHint = true)
    )
    public String updateSubscriptionStatus(
            @McpArg(name = "subscriptionId", description = "UUID of the subscription", required = true) String subscriptionId,
            @McpArg(name = "status", description = "ACTIVE | PAUSED | CANCELLED", required = true) String status
    ) {
        subscriptionService.updateStatus(
                McpInputs.requireUuid(subscriptionId, "subscriptionId"),
                McpInputs.requireEnum(status, "status", SubscriptionStatus.class)
        );
        return "Subscription " + subscriptionId + " status updated to " + status;
    }
}
