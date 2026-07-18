package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreateAccountRequest;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.dto.CreateLoanRequest;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.LoanSummary;
import com.moksh.walletwizzard.dto.RecordLoanPaymentRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.Loan;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.enums.LoanDirection;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.LoanRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final AccountService accountService;
    private final AccountingService accountingService;
    private final EntityManager entityManager;

    @Transactional
    public Loan createLoan(@Valid CreateLoanRequest request) {
        AccountType loanAccountType = request.direction() == LoanDirection.TAKEN
                ? AccountType.LIABILITY : AccountType.ASSET;
        AccountSubType loanSubType = request.direction() == LoanDirection.TAKEN
                ? AccountSubType.LOAN_PAYABLE : AccountSubType.RECEIVABLE;

        Account loanAccount = accountService.createAccount(
                new CreateAccountRequest("Loan — " + request.name(), loanAccountType, loanSubType, null, null));

        Account interestAccount = resolveInterestAccount(request);

        accountingService.record(new RecordTransactionRequest(
                request.startDate() != null ? request.startDate() : LocalDate.now(),
                "Loan disbursement — " + request.name(),
                EntryType.OPENING_BALANCE, null,
                buildDisbursementLines(request, loanAccount.getId())));

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        Loan loan = Loan.builder()
                .user(userRef)
                .account(loanAccount)
                .interestAccount(interestAccount)
                .name(request.name())
                .direction(request.direction())
                .principal(request.principal())
                .interestRate(request.interestRate())
                .emiAmount(request.emiAmount())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .notes(request.notes())
                .build();

        log.info("Creating loan '{}' direction={} principal={}", request.name(), request.direction(), request.principal());
        return loanRepository.save(loan);
    }

    @Transactional
    public void recordPayment(@Valid RecordLoanPaymentRequest request) {
        Loan loan = loanRepository.findByIdWithAccounts(request.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan", request.loanId()));

        accountingService.record(new RecordTransactionRequest(
                request.date(),
                "Loan payment — " + loan.getName(),
                EntryType.LOAN_PAYMENT,
                loan.getId(),
                buildPaymentLines(request, loan)));

        log.info("Recorded payment for loan '{}'", loan.getName());
    }

    public List<LoanSummary> listLoans() {
        List<Loan> loans = loanRepository.findAllWithAccounts();
        List<UUID> accountIds = loans.stream().map(l -> l.getAccount().getId()).toList();
        Map<UUID, BigDecimal> balances = accountService.getBalancesForAccounts(accountIds);
        return loans.stream().map(l -> toSummary(l, balances)).toList();
    }

    public LoanSummary getLoanSummary(UUID loanId) {
        Loan loan = loanRepository.findByIdWithAccounts(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId));
        BigDecimal balance = accountService.getBalance(loan.getAccount().getId());
        return toSummary(loan, Map.of(loan.getAccount().getId(), balance));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Account resolveInterestAccount(CreateLoanRequest request) {
        if (request.interestAccountId() != null) {
            return accountService.getById(request.interestAccountId());
        }
        AccountType fallbackType = request.direction() == LoanDirection.TAKEN
                ? AccountType.EXPENSE : AccountType.INCOME;
        return accountService.listAccounts(fallbackType).stream()
                .filter(Account::isSystem)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No system " + fallbackType + " account found. Provide an explicit interestAccountId."));
    }

    private List<LineRequest> buildDisbursementLines(CreateLoanRequest request, UUID loanAccountId) {
        if (request.direction() == LoanDirection.TAKEN) {
            return List.of(
                    new LineRequest(request.bankAccountId(), request.principal(), EntrySide.DEBIT, "Loan proceeds received"),
                    new LineRequest(loanAccountId, request.principal(), EntrySide.CREDIT, "Loan liability created")
            );
        } else {
            return List.of(
                    new LineRequest(loanAccountId, request.principal(), EntrySide.DEBIT, "Loan asset created"),
                    new LineRequest(request.bankAccountId(), request.principal(), EntrySide.CREDIT, "Loan funds disbursed")
            );
        }
    }

    private List<LineRequest> buildPaymentLines(RecordLoanPaymentRequest request, Loan loan) {
        UUID loanAccountId = loan.getAccount().getId();
        UUID interestAccountId = loan.getInterestAccount() != null
                ? loan.getInterestAccount().getId() : null;

        BigDecimal total = request.totalAmount();
        boolean hasInterest = request.interestPortion().compareTo(BigDecimal.ZERO) > 0
                && interestAccountId != null;

        List<LineRequest> lines = new ArrayList<>();

        if (loan.getDirection() == LoanDirection.TAKEN) {
            lines.add(new LineRequest(request.bankAccountId(), total, EntrySide.CREDIT, "EMI payment"));
            lines.add(new LineRequest(loanAccountId, request.principalPortion(), EntrySide.DEBIT, "Principal"));
            if (hasInterest) {
                lines.add(new LineRequest(interestAccountId, request.interestPortion(), EntrySide.DEBIT, "Interest"));
            } else if (request.interestPortion().compareTo(BigDecimal.ZERO) > 0) {
                lines.set(1, new LineRequest(loanAccountId, total, EntrySide.DEBIT, "Principal + Interest"));
            }
        } else {
            lines.add(new LineRequest(request.bankAccountId(), total, EntrySide.DEBIT, "EMI received"));
            lines.add(new LineRequest(loanAccountId, request.principalPortion(), EntrySide.CREDIT, "Principal"));
            if (hasInterest) {
                lines.add(new LineRequest(interestAccountId, request.interestPortion(), EntrySide.CREDIT, "Interest income"));
            } else if (request.interestPortion().compareTo(BigDecimal.ZERO) > 0) {
                lines.set(1, new LineRequest(loanAccountId, total, EntrySide.CREDIT, "Principal + Interest"));
            }
        }

        return lines;
    }

    private LoanSummary toSummary(Loan loan, Map<UUID, BigDecimal> balances) {
        BigDecimal balance = balances.getOrDefault(loan.getAccount().getId(), BigDecimal.ZERO);
        return new LoanSummary(
                loan.getId(), loan.getName(), loan.getDirection(), loan.getPrincipal(),
                balance, loan.getInterestRate(), loan.getEmiAmount(),
                loan.getStartDate(), loan.getEndDate(), loan.getNotes()
        );
    }
}
