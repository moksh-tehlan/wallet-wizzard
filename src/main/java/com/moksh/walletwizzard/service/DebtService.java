package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreateAccountRequest;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.dto.CreateDebtRequest;
import com.moksh.walletwizzard.dto.DebtSummary;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.DebtRecord;
import com.moksh.walletwizzard.entity.Person;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.enums.DebtDirection;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.DebtRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DebtService {

    private final DebtRecordRepository debtRecordRepository;
    private final PersonService personService;
    private final AccountService accountService;
    private final AccountingService accountingService;
    private final EntityManager entityManager;

    @Transactional
    public DebtRecord createDebt(@Valid CreateDebtRequest request) {
        Person person = personService.getById(request.personId());

        AccountType debtAccountType = request.direction() == DebtDirection.LENT
                ? AccountType.ASSET : AccountType.LIABILITY;
        String prefix = request.direction() == DebtDirection.LENT ? "Receivable" : "Payable";
        String accountName = prefix + " — " + person.getName();

        AccountSubType debtSubType = request.direction() == DebtDirection.LENT
                ? AccountSubType.RECEIVABLE : AccountSubType.OTHER_LIABILITY;

        Account debtAccount = accountService.createAccount(
                new CreateAccountRequest(accountName, debtAccountType, debtSubType, null,
                        "Auto-created for " + request.direction().name().toLowerCase()
                        + " to " + person.getName()));

        LocalDate entryDate = request.date() != null ? request.date() : LocalDate.now();
        String description = request.description() != null ? request.description()
                : (request.direction() == DebtDirection.LENT
                        ? "Lent to " + person.getName()
                        : "Borrowed from " + person.getName());

        accountingService.record(new RecordTransactionRequest(
                entryDate, description, EntryType.OPENING_BALANCE, null,
                buildDebtLines(request, debtAccount.getId())));

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        DebtRecord record = DebtRecord.builder()
                .user(userRef)
                .person(person)
                .account(debtAccount)
                .direction(request.direction())
                .originalAmount(request.amount())
                .description(description)
                .dueDate(request.dueDate())
                .build();

        log.info("Recording debt {} {} with {}", request.direction(), person.getName(), request.amount());
        return debtRecordRepository.save(record);
    }

    public List<DebtSummary> listDebts(DebtDirection direction, UUID personId) {
        List<DebtRecord> records;
        if (personId != null) {
            records = debtRecordRepository.findByPersonWithDetails(personId);
        } else if (direction != null) {
            records = debtRecordRepository.findByDirectionWithDetails(direction);
        } else {
            records = debtRecordRepository.findAllWithDetails();
        }

        // batch-fetch all balances in one query to avoid N+1
        List<UUID> accountIds = records.stream().map(r -> r.getAccount().getId()).toList();
        Map<UUID, BigDecimal> balances = accountService.getBalancesForAccounts(accountIds);

        return records.stream().map(r -> toSummary(r, balances)).toList();
    }

    public DebtSummary getDebtSummary(UUID debtId) {
        DebtRecord record = debtRecordRepository.findById(debtId)
                .orElseThrow(() -> new ResourceNotFoundException("Debt record", debtId));
        return toSummary(record, Map.of(record.getAccount().getId(),
                accountService.getBalance(record.getAccount().getId())));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<LineRequest> buildDebtLines(CreateDebtRequest request, UUID debtAccountId) {
        if (request.direction() == DebtDirection.LENT) {
            return List.of(
                    new LineRequest(debtAccountId, request.amount(), EntrySide.DEBIT, "Receivable created"),
                    new LineRequest(request.bankAccountId(), request.amount(), EntrySide.CREDIT, null)
            );
        } else {
            return List.of(
                    new LineRequest(request.bankAccountId(), request.amount(), EntrySide.DEBIT, null),
                    new LineRequest(debtAccountId, request.amount(), EntrySide.CREDIT, "Payable created")
            );
        }
    }

    private DebtSummary toSummary(DebtRecord record, Map<UUID, BigDecimal> balances) {
        BigDecimal balance = balances.getOrDefault(record.getAccount().getId(), BigDecimal.ZERO);
        return new DebtSummary(
                record.getId(),
                record.getPerson().getId(),
                record.getPerson().getName(),
                record.getDirection(),
                record.getOriginalAmount(),
                balance,
                record.getDueDate(),
                record.getDescription()
        );
    }
}
