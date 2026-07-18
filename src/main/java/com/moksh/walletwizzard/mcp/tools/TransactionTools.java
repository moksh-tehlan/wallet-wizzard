package com.moksh.walletwizzard.mcp.tools;

import tools.jackson.databind.ObjectMapper;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.dto.TransactionFilter;
import com.moksh.walletwizzard.entity.JournalEntry;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.mcp.dto.LineView;
import com.moksh.walletwizzard.mcp.dto.TransactionView;
import com.moksh.walletwizzard.service.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionTools {

    private final AccountingService accountingService;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "record_transaction",
            description = """
                    Records a double-entry journal transaction. Every transaction must have at least
                    two lines where SUM(DEBIT amounts) == SUM(CREDIT amounts). Never violate this.

                    Double-entry rules by account type:
                    - ASSET   — DEBIT increases (cash in), CREDIT decreases (cash out)
                    - LIABILITY — CREDIT increases (new debt), DEBIT decreases (debt paid)
                    - INCOME  — CREDIT increases (earn money), DEBIT decreases (refund)
                    - EXPENSE — DEBIT increases (spend money), CREDIT decreases (return)

                    Common patterns:
                    • Grocery ₹500 (debit card): DEBIT Food&Dining 500, CREDIT Bank Account 500
                    • Salary ₹50000: DEBIT Bank Account 50000, CREDIT Salary 50000
                    • Credit card purchase ₹1000: DEBIT Shopping 1000, CREDIT Credit Card 1000
                    • Pay credit card ₹5000: DEBIT Credit Card 5000, CREDIT Bank Account 5000
                    • Savings transfer ₹10000: DEBIT Savings Account 10000, CREDIT Checking Account 10000

                    entryType values: EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT | LOAN_PAYMENT |
                                      SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String recordTransaction(
            @McpArg(name = "date", description = "Transaction date ISO format YYYY-MM-DD", required = true)
            String date,
            @McpArg(name = "description", description = "Human-readable description of the transaction", required = true)
            String description,
            @McpArg(name = "entryType", description = "Category: EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT | LOAN_PAYMENT | SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT", required = true)
            String entryType,
            @McpArg(name = "lines", description = "List of journal entry lines. Each line: {accountId, amount, side (DEBIT|CREDIT), memo}", required = true)
            List<LineInput> lines,
            @McpArg(name = "referenceId", description = "UUID of a related loan/subscription/debt record (optional)", required = false)
            String referenceId
    ) {
        List<LineRequest> lineRequests = lines.stream()
                .map(l -> new LineRequest(
                        McpInputs.requireUuid(l.accountId(), "lines[].accountId"),
                        McpInputs.requireAmount(l.amount(), "lines[].amount"),
                        McpInputs.requireEnum(l.side(), "lines[].side", EntrySide.class),
                        l.memo()
                ))
                .toList();

        JournalEntry entry = accountingService.record(new RecordTransactionRequest(
                McpInputs.requireDate(date, "date"),
                description,
                McpInputs.requireEnum(entryType, "entryType", EntryType.class),
                McpInputs.parseUuid(referenceId, "referenceId"),
                lineRequests
        ));
        return "Transaction recorded: id=" + entry.getId()
                + ", date=" + entry.getDate()
                + ", lines=" + entry.getLines().size();
    }

    @McpTool(
            name = "get_transactions",
            description = """
                    Lists journal entries (transactions) with their full double-entry lines.
                    Filter by date range, account, or entry type. Defaults to the last 50 entries.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String getTransactions(
            @McpArg(name = "accountId", description = "Filter to transactions touching this account UUID (optional)", required = false)
            String accountId,
            @McpArg(name = "from", description = "Start date YYYY-MM-DD (optional)", required = false)
            String from,
            @McpArg(name = "to", description = "End date YYYY-MM-DD (optional)", required = false)
            String to,
            @McpArg(name = "entryType", description = "Filter by type: EXPENSE | INCOME | TRANSFER | ... (optional)", required = false)
            String entryType,
            @McpArg(name = "limit", description = "Max results 1-500, default 50", required = false)
            Integer limit
    ) {
        var filter = new TransactionFilter(
                McpInputs.parseUuid(accountId, "accountId"),
                McpInputs.parseDate(from, "from"),
                McpInputs.parseDate(to, "to"),
                McpInputs.parseEnum(entryType, "entryType", EntryType.class),
                limit != null ? limit : 50
        );
        List<JournalEntry> entries = accountingService.getTransactions(filter);
        return objectMapper.writeValueAsString(entries.stream()
                .map(e -> new TransactionView(
                        e.getId().toString(),
                        e.getDate().toString(),
                        e.getDescription(),
                        e.getEntryType().name(),
                        e.getLines().stream().map(l -> new LineView(
                                l.getAccount().getId().toString(),
                                l.getAccount().getName(),
                                l.getAmount().toPlainString(),
                                l.getSide().name(),
                                l.getMemo()
                        )).toList()
                ))
                .toList());
    }
}
