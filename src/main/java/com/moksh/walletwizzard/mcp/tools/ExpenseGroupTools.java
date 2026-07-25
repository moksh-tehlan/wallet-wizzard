package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.GroupBalanceDto;
import com.moksh.walletwizzard.dto.GroupExpenseDto;
import com.moksh.walletwizzard.dto.GroupSummaryDto;
import com.moksh.walletwizzard.entity.ExpenseGroup;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.ExpenseGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpenseGroupTools {

    private final ExpenseGroupService expenseGroupService;

    @McpTool(
            name = "create_expense_group",
            description = """
                    Creates a shared expense group (Splitwise-style).
                    Add people who will share expenses with you. The owner (you) is always
                    an implicit member — do not add yourself as a person.

                    memberPersonIds: list of person UUIDs to add as members.
                    Use add_person first if the people don't exist yet.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String createExpenseGroup(
            @McpArg(name = "name", description = "Group name e.g. 'Goa Trip', 'Flat Expenses'", required = true)
            String name,
            @McpArg(name = "memberPersonIds", description = "List of person UUIDs to add as members", required = true)
            List<String> memberPersonIds,
            @McpArg(name = "notes", description = "Optional notes about the group", required = false)
            String notes
    ) {
        List<UUID> personIds = memberPersonIds.stream()
                .map(id -> McpInputs.requireUuid(id, "memberPersonIds"))
                .toList();
        ExpenseGroup group = expenseGroupService.createGroup(name, McpInputs.blankToNull(notes), personIds);
        return "Group created: id=" + group.getId()
                + ", name=" + group.getName()
                + ", members=" + personIds.size()
                + ". Use add_group_expense to start adding shared expenses.";
    }

    @McpTool(
            name = "add_group_member",
            description = "Adds a person to an existing expense group.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addGroupMember(
            @McpArg(name = "groupId", description = "UUID of the expense group", required = true) String groupId,
            @McpArg(name = "personId", description = "UUID of the person to add", required = true) String personId
    ) {
        var member = expenseGroupService.addMember(
                McpInputs.requireUuid(groupId, "groupId"),
                McpInputs.requireUuid(personId, "personId"));
        return "Member added: " + member.getPerson().getName() + " → group " + groupId;
    }

    @McpTool(
            name = "add_group_expense",
            description = """
                    Adds a shared expense to a group and splits it equally among all members (including you).

                    If YOU paid: provide expenseAccountId + bankAccountId. A journal entry is posted
                    automatically (DEBIT expense account, CREDIT bank). Each member's share is tracked.

                    If SOMEONE ELSE paid: provide paidByPersonId (omit bank/expense accounts).
                    Your share of the expense is tracked as what you owe that person.

                    Each member's share = totalAmount ÷ (number of members + 1 for you).

                    Use get_group_balance to see running totals after adding expenses.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public GroupExpenseDto addGroupExpense(
            @McpArg(name = "groupId", description = "UUID of the expense group", required = true)
            String groupId,
            @McpArg(name = "description", description = "What was spent on e.g. 'Hotel Night 1', 'Dinner at Spice Garden'", required = true)
            String description,
            @McpArg(name = "totalAmount", description = "Total amount paid e.g. '6000'", required = true)
            String totalAmount,
            @McpArg(name = "expenseAccountId", description = "UUID of expense category account (required when you paid)", required = false)
            String expenseAccountId,
            @McpArg(name = "bankAccountId", description = "UUID of bank/wallet account used to pay (required when you paid)", required = false)
            String bankAccountId,
            @McpArg(name = "date", description = "Expense date YYYY-MM-DD (defaults to today)", required = false)
            String date,
            @McpArg(name = "paidByPersonId", description = "UUID of person who paid (omit if you paid)", required = false)
            String paidByPersonId
    ) {
        return expenseGroupService.addExpense(
                McpInputs.requireUuid(groupId, "groupId"),
                description,
                McpInputs.requireAmount(totalAmount, "totalAmount"),
                McpInputs.parseUuid(paidByPersonId, "paidByPersonId"),
                McpInputs.parseUuid(expenseAccountId, "expenseAccountId"),
                McpInputs.parseUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseDate(date, "date")
        );
    }

    @McpTool(
            name = "list_groups",
            description = "Lists all active expense groups with member count and total expenses.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<GroupSummaryDto> listGroups() {
        return expenseGroupService.listGroups();
    }

    @McpTool(
            name = "list_group_expenses",
            description = "Lists all expenses in a group ordered by date (most recent first), with per-member splits.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<GroupExpenseDto> listGroupExpenses(
            @McpArg(name = "groupId", description = "UUID of the expense group", required = true) String groupId
    ) {
        return expenseGroupService.listGroupExpenses(McpInputs.requireUuid(groupId, "groupId"));
    }

    @McpTool(
            name = "get_group_balance",
            description = """
                    Shows the real-time balance for each member in a group.
                    theyOweYou: sum of their unsettled shares from expenses you paid.
                    youOweThem: sum of your unsettled share from expenses they paid.
                    net: positive = they owe you, negative = you owe them.

                    Use settle_group_member to record a settlement when someone pays up.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public GroupBalanceDto getGroupBalance(
            @McpArg(name = "groupId", description = "UUID of the expense group", required = true) String groupId
    ) {
        return expenseGroupService.getGroupBalance(McpInputs.requireUuid(groupId, "groupId"));
    }

    @McpTool(
            name = "settle_group_member",
            description = """
                    Records settlement of a member's balance in a group.
                    Use after get_group_balance to see who owes what.

                    If net > 0 (they owe you): records money you received (DEBIT bank).
                    If net < 0 (you owe them): records money you paid out (CREDIT bank).

                    amount is optional — defaults to settling the full outstanding balance.
                    For partial settlements, pass the amount explicitly.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public GroupBalanceDto settleGroupMember(
            @McpArg(name = "groupId", description = "UUID of the expense group", required = true) String groupId,
            @McpArg(name = "personId", description = "UUID of the person settling", required = true) String personId,
            @McpArg(name = "bankAccountId", description = "UUID of bank/wallet account for the settlement", required = true) String bankAccountId,
            @McpArg(name = "amount", description = "Amount to settle (omit to settle the full outstanding balance)", required = false) String amount,
            @McpArg(name = "date", description = "Settlement date YYYY-MM-DD (defaults to today)", required = false) String date
    ) {
        return expenseGroupService.settleGroupMember(
                McpInputs.requireUuid(groupId, "groupId"),
                McpInputs.requireUuid(personId, "personId"),
                McpInputs.requireUuid(bankAccountId, "bankAccountId"),
                McpInputs.parseAmount(amount, "amount"),
                McpInputs.parseDate(date, "date")
        );
    }
}
