package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.dto.AccountShareSummaryDto;
import com.moksh.walletwizzard.entity.AccountParticipant;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.service.AccountParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SharedLiabilityTools {

    private final AccountParticipantService accountParticipantService;

    @McpTool(
            name = "add_account_participant",
            description = """
                    Adds a co-borrower / co-user to ANY account — credit cards, loan accounts,
                    informal liabilities, or any other account type.

                    Use this when someone shares a liability with you:
                    - Partner uses your credit card → add them as 50% participant
                    - Sibling co-signed a personal loan → add as 50% participant
                    - Flatmate shares a utility bill account → add as participant

                    sharePercent: their portion (e.g. 50 for 50%). Must be > 0 and ≤ 100.
                    Once added, get_person_balance will automatically include their share of the
                    account's current balance in the net amount they owe you.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addAccountParticipant(
            @McpArg(name = "accountId", description = "UUID of the account to share", required = true) String accountId,
            @McpArg(name = "personId", description = "UUID of the co-borrower (from list_people)", required = true) String personId,
            @McpArg(name = "sharePercent", description = "Their share as a percentage e.g. 50 for 50%", required = true) String sharePercent,
            @McpArg(name = "notes", description = "Optional notes e.g. 'Partner on joint credit card'", required = false) String notes
    ) {
        BigDecimal share = McpInputs.requireAmount(sharePercent, "sharePercent");
        AccountParticipant participant = accountParticipantService.addParticipant(
                McpInputs.requireUuid(accountId, "accountId"),
                McpInputs.requireUuid(personId, "personId"),
                share,
                McpInputs.blankToNull(notes)
        );
        return "Participant added: id=" + participant.getId()
                + ", share=" + share + "%. Their share will now appear in get_person_balance.";
    }

    @McpTool(
            name = "get_account_share_summary",
            description = """
                    Shows the current balance of an account and each participant's outstanding share.
                    outstandingShare = currentBalance × sharePercent.

                    For a credit card with ₹50,000 balance and a 50% participant:
                    → outstandingShare = ₹25,000 (they owe you this much of the current bill).

                    Also visible in get_person_balance for each person.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public AccountShareSummaryDto getAccountShareSummary(
            @McpArg(name = "accountId", description = "UUID of the account", required = true) String accountId
    ) {
        return accountParticipantService.getShareSummary(
                McpInputs.requireUuid(accountId, "accountId"));
    }

    @McpTool(
            name = "remove_account_participant",
            description = """
                    Removes a co-borrower/participant from an account.
                    Use this when someone's share should no longer be tracked dynamically
                    against the account balance — e.g. when you want to convert their
                    outstanding share to a fixed direct debt via add_debt instead.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true)
    )
    public String removeAccountParticipant(
            @McpArg(name = "accountId", description = "UUID of the account", required = true) String accountId,
            @McpArg(name = "personId", description = "UUID of the person to remove", required = true) String personId
    ) {
        accountParticipantService.removeParticipant(
                McpInputs.requireUuid(accountId, "accountId"),
                McpInputs.requireUuid(personId, "personId")
        );
        return "Participant removed from account " + accountId + ".";
    }

    @McpTool(
            name = "list_account_participants",
            description = "Lists all co-borrowers/participants for a given account with their share percentages.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public List<AccountShareSummaryDto.ParticipantShareView> listAccountParticipants(
            @McpArg(name = "accountId", description = "UUID of the account", required = true) String accountId
    ) {
        return accountParticipantService.getShareSummary(
                McpInputs.requireUuid(accountId, "accountId")).participants();
    }
}
