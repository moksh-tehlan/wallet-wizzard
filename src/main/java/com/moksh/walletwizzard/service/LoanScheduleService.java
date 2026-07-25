package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.*;
import com.moksh.walletwizzard.entity.*;
import com.moksh.walletwizzard.enums.DebtDirection;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.enums.InstallmentStatus;
import com.moksh.walletwizzard.enums.LoanDirection;
import com.moksh.walletwizzard.exception.InvalidInputException;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.LoanInstallmentRepository;
import com.moksh.walletwizzard.repository.LoanParticipantRepository;
import com.moksh.walletwizzard.repository.LoanRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoanScheduleService {

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanParticipantRepository participantRepository;
    private final AccountingService accountingService;
    private final AccountService accountService;
    private final PersonService personService;
    private final EntityManager entityManager;

    // ── Schedule Generation ──────────────────────────────────────────────────

    @Transactional
    public List<InstallmentDto> generateSchedule(UUID loanId, int numberOfInstallments, LocalDate firstDueDate) {
        Loan loan = requireLoan(loanId);

        if (installmentRepository.existsByLoanId(loanId)) {
            throw new InvalidInputException(
                    "Loan '" + loan.getName() + "' already has a schedule. Use get_loan_schedule to view it.");
        }
        if (numberOfInstallments < 1 || numberOfInstallments > 600) {
            throw new InvalidInputException("numberOfInstallments must be between 1 and 600.");
        }

        BigDecimal monthlyRate = loan.getInterestRate() != null
                ? loan.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal emi = computeEmi(loan.getPrincipal(), monthlyRate, numberOfInstallments, loan.getEmiAmount());
        LocalDate dueDate = firstDueDate != null ? firstDueDate
                : (loan.getStartDate() != null ? loan.getStartDate().plusMonths(1) : LocalDate.now().plusMonths(1));

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        BigDecimal balance = loan.getPrincipal();
        List<LoanInstallment> installments = new ArrayList<>(numberOfInstallments);

        for (int i = 1; i <= numberOfInstallments; i++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = emi.subtract(interest);

            // Last installment clears remaining balance
            if (i == numberOfInstallments) {
                principal = balance;
                interest = emi.subtract(principal).max(BigDecimal.ZERO);
            }

            principal = principal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = principal.add(interest);

            installments.add(LoanInstallment.builder()
                    .user(userRef)
                    .loan(loan)
                    .installmentNo(i)
                    .dueDate(dueDate)
                    .principalAmount(principal)
                    .interestAmount(interest)
                    .totalAmount(total)
                    .status(InstallmentStatus.SCHEDULED)
                    .build());

            balance = balance.subtract(principal).max(BigDecimal.ZERO);
            dueDate = dueDate.plusMonths(1);
        }

        List<LoanInstallment> saved = installmentRepository.saveAll(installments);
        log.info("Generated {} installments for loan '{}'", saved.size(), loan.getName());
        return saved.stream().map(this::toDto).toList();
    }

    @Transactional
    public List<InstallmentDto> importSchedule(UUID loanId, List<InstallmentInput> inputs) {
        Loan loan = requireLoan(loanId);

        if (installmentRepository.existsByLoanId(loanId)) {
            throw new InvalidInputException(
                    "Loan '" + loan.getName() + "' already has a schedule.");
        }

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        List<LoanInstallment> installments = new ArrayList<>(inputs.size());

        for (int i = 0; i < inputs.size(); i++) {
            InstallmentInput in = inputs.get(i);
            installments.add(LoanInstallment.builder()
                    .user(userRef)
                    .loan(loan)
                    .installmentNo(i + 1)
                    .dueDate(in.dueDate())
                    .principalAmount(in.principalAmount())
                    .interestAmount(in.interestAmount() != null ? in.interestAmount() : BigDecimal.ZERO)
                    .totalAmount(in.principalAmount().add(in.interestAmount() != null ? in.interestAmount() : BigDecimal.ZERO))
                    .status(InstallmentStatus.SCHEDULED)
                    .build());
        }

        List<LoanInstallment> saved = installmentRepository.saveAll(installments);
        log.info("Imported {} installments for loan '{}'", saved.size(), loan.getName());
        return saved.stream().map(this::toDto).toList();
    }

    public List<InstallmentDto> getSchedule(UUID loanId) {
        requireLoan(loanId);
        return installmentRepository.findByLoanIdOrderByInstallmentNo(loanId)
                .stream().map(this::toDto).toList();
    }

    // ── Installment Payment ──────────────────────────────────────────────────

    @Transactional
    public InstallmentDto payInstallment(UUID installmentId, UUID bankAccountId, LocalDate date) {
        LoanInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Installment", installmentId));

        if (installment.getStatus() == InstallmentStatus.PAID) {
            throw new InvalidInputException("Installment #" + installment.getInstallmentNo() + " is already paid.");
        }

        Loan loan = loanRepository.findByIdWithAccounts(installment.getLoan().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan", installment.getLoan().getId()));

        LocalDate paymentDate = date != null ? date : LocalDate.now();

        JournalEntry entry = accountingService.record(new RecordTransactionRequest(
                paymentDate,
                "Installment #" + installment.getInstallmentNo() + " — " + loan.getName(),
                EntryType.INSTALLMENT_PAYMENT,
                loan.getId(),
                buildInstallmentLines(installment, loan, bankAccountId)
        ));

        installment.setStatus(InstallmentStatus.PAID);
        installment.setPaidDate(paymentDate);
        installment.setJournalEntryId(entry.getId());
        installmentRepository.save(installment);

        log.info("Paid installment #{} for loan '{}'", installment.getInstallmentNo(), loan.getName());
        return toDto(installment);
    }

    // ── Participants ─────────────────────────────────────────────────────────

    @Transactional
    public LoanParticipant addParticipant(UUID loanId, UUID personId, BigDecimal sharePercent, String notes) {
        Loan loan = requireLoan(loanId);
        Person person = personService.getById(personId);

        if (participantRepository.existsByLoanIdAndPersonId(loanId, personId)) {
            throw new InvalidInputException(
                    person.getName() + " is already a participant in loan '" + loan.getName() + "'.");
        }

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        LoanParticipant participant = LoanParticipant.builder()
                .user(userRef)
                .loan(loan)
                .person(person)
                .sharePercent(sharePercent)
                .notes(notes)
                .build();

        LoanParticipant saved = participantRepository.save(participant);
        log.info("Added {} ({}%) as participant in loan '{}'", person.getName(), sharePercent, loan.getName());
        return saved;
    }

    public LoanShareSummaryDto getShareSummary(UUID loanId) {
        Loan loan = requireLoan(loanId);
        List<LoanParticipant> participants = participantRepository.findByLoanIdWithPerson(loanId);
        List<LoanInstallment> installments = installmentRepository.findByLoanIdOrderByInstallmentNo(loanId);

        BigDecimal totalScheduled = installments.stream()
                .map(LoanInstallment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidTotal = installments.stream()
                .filter(i -> i.getStatus() == InstallmentStatus.PAID)
                .map(LoanInstallment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dueTotal = installments.stream()
                .filter(i -> i.getStatus() == InstallmentStatus.DUE)
                .map(LoanInstallment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal scheduledTotal = installments.stream()
                .filter(i -> i.getStatus() == InstallmentStatus.SCHEDULED)
                .map(LoanInstallment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LoanShareSummaryDto.ParticipantShareDto> participantDtos = participants.stream()
                .map(p -> {
                    BigDecimal factor = p.getSharePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                    return new LoanShareSummaryDto.ParticipantShareDto(
                            p.getId(),
                            p.getPerson().getId(),
                            p.getPerson().getName(),
                            p.getSharePercent(),
                            totalScheduled.multiply(factor).setScale(2, RoundingMode.HALF_UP),
                            paidTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP),
                            dueTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP),
                            scheduledTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP)
                    );
                })
                .toList();

        return new LoanShareSummaryDto(loanId, loan.getName(), totalScheduled, participantDtos);
    }

    // ── Person Balance ───────────────────────────────────────────────────────

    public PersonBalanceDto getPersonBalance(UUID personId) {
        Person person = personService.getById(personId);

        // Direct debts via account balances (loaded by DebtService)
        List<DebtRecord> debtRecords = loadDebtRecordsForPerson(personId);
        List<UUID> accountIds = debtRecords.stream().map(dr -> dr.getAccount().getId()).toList();
        java.util.Map<UUID, BigDecimal> balances = accountIds.isEmpty()
                ? java.util.Map.of()
                : accountService.getBalancesForAccounts(accountIds);

        List<PersonBalanceDto.DirectDebtView> directDebts = debtRecords.stream()
                .map(dr -> {
                    BigDecimal bal = balances.getOrDefault(dr.getAccount().getId(), BigDecimal.ZERO);
                    return new PersonBalanceDto.DirectDebtView(
                            dr.getId(), dr.getDirection(), dr.getDescription(),
                            dr.getOriginalAmount(), bal, dr.getDueDate()
                    );
                })
                .toList();

        BigDecimal directNetBalance = directDebts.stream()
                .map(d -> d.direction() == DebtDirection.LENT ? d.outstandingBalance() : d.outstandingBalance().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Loan participations
        List<LoanParticipant> participations = participantRepository.findByPersonIdWithLoan(personId);
        List<PersonBalanceDto.LoanParticipationView> loanViews = new ArrayList<>();
        BigDecimal loanNetBalance = BigDecimal.ZERO;

        for (LoanParticipant p : participations) {
            List<LoanInstallment> installments = installmentRepository
                    .findByLoanIdOrderByInstallmentNo(p.getLoan().getId());

            BigDecimal factor = p.getSharePercent().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

            BigDecimal paidByUser = installments.stream()
                    .filter(i -> i.getStatus() == InstallmentStatus.PAID)
                    .map(LoanInstallment::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(factor).setScale(2, RoundingMode.HALF_UP);

            BigDecimal currentlyDue = installments.stream()
                    .filter(i -> i.getStatus() == InstallmentStatus.DUE)
                    .map(LoanInstallment::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(factor).setScale(2, RoundingMode.HALF_UP);

            BigDecimal scheduledFuture = installments.stream()
                    .filter(i -> i.getStatus() == InstallmentStatus.SCHEDULED)
                    .map(LoanInstallment::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(factor).setScale(2, RoundingMode.HALF_UP);

            loanViews.add(new PersonBalanceDto.LoanParticipationView(
                    p.getLoan().getId(), p.getLoan().getName(), p.getSharePercent(),
                    paidByUser, currentlyDue, scheduledFuture
            ));

            // For TAKEN loans: participant owes user (positive); GIVEN: user owes participant (negative)
            BigDecimal owedByPerson = paidByUser.add(currentlyDue);
            if (p.getLoan().getDirection() == LoanDirection.GIVEN) owedByPerson = owedByPerson.negate();
            loanNetBalance = loanNetBalance.add(owedByPerson);
        }

        return new PersonBalanceDto(
                person.getId(), person.getName(),
                directDebts, loanViews,
                directNetBalance.add(loanNetBalance)
        );
    }

    // ── Upcoming Payments ────────────────────────────────────────────────────

    public List<UpcomingPaymentDto> getUpcomingPayments(int withinDays) {
        LocalDate cutoff = LocalDate.now().plusDays(withinDays);
        return installmentRepository.findUpcomingInstallments(cutoff).stream()
                .map(i -> new UpcomingPaymentDto(
                        "LOAN_INSTALLMENT",
                        i.getLoan().getId(),
                        i.getLoan().getName(),
                        i.getDueDate(),
                        i.getTotalAmount(),
                        "Installment #" + i.getInstallmentNo() + " (" + i.getStatus() + ")"
                ))
                .toList();
    }

    // ── Scheduler hook ───────────────────────────────────────────────────────

    @Transactional
    public int markInstallmentsDue(LocalDate today) {
        List<LoanInstallment> toMark = installmentRepository.findScheduledInstallmentsDueBy(today);
        toMark.forEach(i -> i.setStatus(InstallmentStatus.DUE));
        installmentRepository.saveAll(toMark);
        if (!toMark.isEmpty()) {
            log.info("Marked {} installments as DUE", toMark.size());
        }
        return toMark.size();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Loan requireLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId));
    }

    private BigDecimal computeEmi(BigDecimal principal, BigDecimal monthlyRate, int n, BigDecimal existingEmi) {
        if (existingEmi != null) return existingEmi;
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        // EMI = P × r × (1+r)^n / ((1+r)^n − 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow = onePlusR.pow(n, MathContext.DECIMAL128);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(pow);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private List<LineRequest> buildInstallmentLines(LoanInstallment installment, Loan loan, UUID bankAccountId) {
        UUID loanAccountId = loan.getAccount().getId();
        boolean hasInterest = installment.getInterestAmount().compareTo(BigDecimal.ZERO) > 0
                && loan.getInterestAccount() != null;

        List<LineRequest> lines = new ArrayList<>();

        if (loan.getDirection() == LoanDirection.TAKEN) {
            lines.add(new LineRequest(bankAccountId, installment.getTotalAmount(), EntrySide.CREDIT, "EMI payment"));
            lines.add(new LineRequest(loanAccountId, installment.getPrincipalAmount(), EntrySide.DEBIT, "Principal"));
            if (hasInterest) {
                lines.add(new LineRequest(loan.getInterestAccount().getId(), installment.getInterestAmount(), EntrySide.DEBIT, "Interest"));
            } else if (installment.getInterestAmount().compareTo(BigDecimal.ZERO) > 0) {
                // no explicit interest account — absorb into loan account
                lines.set(1, new LineRequest(loanAccountId, installment.getTotalAmount(), EntrySide.DEBIT, "Principal + Interest"));
            }
        } else {
            lines.add(new LineRequest(bankAccountId, installment.getTotalAmount(), EntrySide.DEBIT, "EMI received"));
            lines.add(new LineRequest(loanAccountId, installment.getPrincipalAmount(), EntrySide.CREDIT, "Principal"));
            if (hasInterest) {
                lines.add(new LineRequest(loan.getInterestAccount().getId(), installment.getInterestAmount(), EntrySide.CREDIT, "Interest income"));
            } else if (installment.getInterestAmount().compareTo(BigDecimal.ZERO) > 0) {
                lines.set(1, new LineRequest(loanAccountId, installment.getTotalAmount(), EntrySide.CREDIT, "Principal + Interest"));
            }
        }

        return lines;
    }

    private List<DebtRecord> loadDebtRecordsForPerson(UUID personId) {
        // Load via entity manager to avoid circular dependency
        return entityManager.createQuery("""
                SELECT dr FROM DebtRecord dr
                JOIN FETCH dr.account
                WHERE dr.person.id = :personId
                """, DebtRecord.class)
                .setParameter("personId", personId)
                .getResultList();
    }

    private InstallmentDto toDto(LoanInstallment i) {
        return new InstallmentDto(
                i.getId(), i.getInstallmentNo(), i.getDueDate(),
                i.getPrincipalAmount(), i.getInterestAmount(), i.getTotalAmount(),
                i.getStatus(), i.getPaidDate()
        );
    }

    public record InstallmentInput(
            LocalDate dueDate,
            BigDecimal principalAmount,
            BigDecimal interestAmount
    ) {}
}
