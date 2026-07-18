package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.dto.TransactionFilter;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.JournalEntry;
import com.moksh.walletwizzard.entity.JournalEntryLine;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.exception.DuplicateReferenceException;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.exception.UnbalancedEntryException;
import com.moksh.walletwizzard.repository.JournalEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountingService {

    private final JournalEntryRepository journalEntryRepository;
    private final EntityManager entityManager;

    @Transactional
    public JournalEntry record(@Valid RecordTransactionRequest request) {
        validateBalance(request.lines());

        if (request.referenceId() != null
                && journalEntryRepository.existsByReferenceIdAndEntryType(request.referenceId(), request.entryType())) {
            throw new DuplicateReferenceException(request.referenceId());
        }

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());

        JournalEntry entry = JournalEntry.builder()
                .user(userRef)
                .date(request.date())
                .description(request.description())
                .entryType(request.entryType())
                .referenceId(request.referenceId())
                .build();

        List<JournalEntryLine> lines = request.lines().stream()
                .map(l -> buildLine(l, entry, userRef))
                .toList();

        entry.getLines().addAll(lines);

        log.info("Recording {} journal entry '{}' with {} lines", request.entryType(), request.description(), lines.size());
        return journalEntryRepository.save(entry);
    }

    public JournalEntry getById(UUID id) {
        return journalEntryRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry", id));
    }

    public List<JournalEntry> getTransactions(TransactionFilter filter) {
        var pageable = PageRequest.of(0, filter.limit());
        if (filter.accountId() != null) {
            return journalEntryRepository.findByAccountWithLines(
                    filter.accountId(), filter.from(), filter.to(), pageable);
        }
        return journalEntryRepository.findByFiltersWithLines(
                filter.from(), filter.to(), filter.entryType(), pageable);
    }

    public List<JournalEntry> getByReference(UUID referenceId) {
        return journalEntryRepository.findByReferenceIdWithLines(referenceId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateBalance(List<LineRequest> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException(
                    "A journal entry must have at least 2 lines (one DEBIT and one CREDIT).");
        }

        BigDecimal totalDebit = lines.stream()
                .filter(l -> l.side() == EntrySide.DEBIT)
                .map(LineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = lines.stream()
                .filter(l -> l.side() == EntrySide.CREDIT)
                .map(LineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Entry must have at least one DEBIT line.");
        }
        if (totalCredit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Entry must have at least one CREDIT line.");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new UnbalancedEntryException(totalDebit, totalCredit);
        }
    }

    private JournalEntryLine buildLine(LineRequest request, JournalEntry entry, User userRef) {
        Account accountRef = entityManager.getReference(Account.class, request.accountId());
        return JournalEntryLine.builder()
                .user(userRef)
                .journalEntry(entry)
                .account(accountRef)
                .amount(request.amount())
                .side(request.side())
                .memo(request.memo())
                .build();
    }
}
