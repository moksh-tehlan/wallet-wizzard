package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreateAccountRequest;
import com.moksh.walletwizzard.dto.CreateInvestmentRequest;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.Investment;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.enums.InvestmentType;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.InvestmentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvestmentService {

    private static final BigDecimal DEFAULT_EPF_RATE = new BigDecimal("0.0825");

    private final InvestmentRepository investmentRepository;
    private final AccountService accountService;
    private final AccountingService accountingService;
    private final MfNavService mfNavService;
    private final EntityManager entityManager;

    @Transactional
    public Investment addInvestment(CreateInvestmentRequest req) {
        validate(req);

        Account investmentAccount = accountService.createAccount(
                new CreateAccountRequest(req.name(), AccountType.ASSET, AccountSubType.OTHER_ASSET, null, null));

        LocalDate today = req.startDate() != null ? req.startDate() : LocalDate.now();
        postOpeningEntry(req, investmentAccount.getId(), today);

        BigDecimal currentValue = computeInitialValue(req);

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        Investment investment = Investment.builder()
                .user(userRef)
                .account(investmentAccount)
                .type(req.type())
                .name(req.name())
                .investedAmount(req.amount())
                .currentValue(currentValue)
                .units(req.units())
                .schemeCode(req.schemeCode())
                .interestRate(effectiveRate(req))
                .monthlyContribution(req.monthlyContribution())
                .startDate(today)
                .maturityDate(req.maturityDate())
                .lastRefreshedAt(LocalDate.now())
                .notes(req.notes())
                .build();

        log.info("Added investment '{}' type={} amount={}", req.name(), req.type(), req.amount());
        return investmentRepository.save(investment);
    }

    @Transactional
    public Investment recordContribution(UUID investmentId, BigDecimal amount, BigDecimal unitsAdded, UUID bankAccountId) {
        Investment inv = requireInvestment(investmentId);

        List<LineRequest> lines = bankAccountId != null
                ? List.of(
                        new LineRequest(inv.getAccount().getId(), amount, EntrySide.DEBIT, "Contribution — " + inv.getName()),
                        new LineRequest(bankAccountId, amount, EntrySide.CREDIT, null))
                : List.of(
                        new LineRequest(inv.getAccount().getId(), amount, EntrySide.DEBIT, "Contribution — " + inv.getName()));

        if (lines.size() == 1) {
            // Single-line journal won't balance — use OPENING_BALANCE-style (no bank counterpart)
            // Create a balanced entry with Opening Equity placeholder
            postSingleSidedContribution(inv, amount);
        } else {
            accountingService.record(new RecordTransactionRequest(
                    LocalDate.now(), "Contribution — " + inv.getName(),
                    EntryType.TRANSFER, inv.getId(), lines));
        }

        inv.setInvestedAmount(inv.getInvestedAmount().add(amount));
        if (unitsAdded != null && inv.getUnits() != null) {
            inv.setUnits(inv.getUnits().add(unitsAdded));
        }
        inv.setCurrentValue(recompute(inv));
        log.info("Recorded contribution of {} to '{}' (total invested: {})", amount, inv.getName(), inv.getInvestedAmount());
        return inv;
    }

    @Transactional
    public List<Investment> refreshAll() {
        List<Investment> all = investmentRepository.findAllWithAccount();
        for (Investment inv : all) {
            try {
                inv.setCurrentValue(recompute(inv));
                inv.setLastRefreshedAt(LocalDate.now());
            } catch (Exception e) {
                log.warn("Failed to refresh '{}': {}", inv.getName(), e.getMessage());
            }
        }
        return investmentRepository.saveAll(all);
    }

    @Transactional
    public Investment closeInvestment(UUID investmentId, UUID bankAccountId, LocalDate date) {
        Investment inv = requireInvestment(investmentId);
        LocalDate closeDate = date != null ? date : LocalDate.now();

        BigDecimal balance = accountService.getBalance(inv.getAccount().getId());

        accountingService.record(new RecordTransactionRequest(
                closeDate, "Closure — " + inv.getName(),
                EntryType.TRANSFER, inv.getId(),
                List.of(
                        new LineRequest(bankAccountId, balance, EntrySide.DEBIT, "Investment proceeds"),
                        new LineRequest(inv.getAccount().getId(), balance, EntrySide.CREDIT, null))));

        log.info("Closed investment '{}', returned {} to bank", inv.getName(), balance);
        return inv;
    }

    public List<Investment> getPortfolio(InvestmentType type) {
        return type != null
                ? investmentRepository.findByTypeWithAccount(type)
                : investmentRepository.findAllWithAccount();
    }

    public List<MfNavService.SchemeResult> searchMutualFunds(String query) {
        return mfNavService.searchSchemes(query);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validate(CreateInvestmentRequest req) {
        if (req.type() == InvestmentType.MUTUAL_FUND) {
            if (req.schemeCode() == null || req.schemeCode().isBlank())
                throw new IllegalArgumentException("schemeCode is required for MUTUAL_FUND.");
            if (req.units() == null || req.units().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("units is required and must be > 0 for MUTUAL_FUND.");
        }
        if (req.type() == InvestmentType.FD || req.type() == InvestmentType.RD) {
            if (req.interestRate() == null)
                throw new IllegalArgumentException("interestRate is required for " + req.type() + ".");
            if (req.maturityDate() == null)
                throw new IllegalArgumentException("maturityDate is required for " + req.type() + ".");
        }
        if (req.type() == InvestmentType.RD && req.monthlyContribution() == null)
            throw new IllegalArgumentException("monthlyContribution is required for RD.");
    }

    private BigDecimal effectiveRate(CreateInvestmentRequest req) {
        if (req.interestRate() != null) return req.interestRate();
        return req.type() == InvestmentType.EPF ? DEFAULT_EPF_RATE : null;
    }

    private BigDecimal computeInitialValue(CreateInvestmentRequest req) {
        if (req.type() == InvestmentType.MUTUAL_FUND) {
            try {
                BigDecimal nav = mfNavService.getLatestNav(req.schemeCode());
                return req.units().multiply(nav).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                log.warn("Could not fetch NAV for scheme {}, using invested amount as initial value", req.schemeCode());
                return req.amount();
            }
        }
        return req.amount(); // FD/RD/EPF start at invested amount
    }

    private BigDecimal recompute(Investment inv) {
        return switch (inv.getType()) {
            case MUTUAL_FUND -> recomputeMf(inv);
            case FD -> recomputeFd(inv);
            case RD -> recomputeRd(inv);
            case EPF -> recomputeEpf(inv);
        };
    }

    private BigDecimal recomputeMf(Investment inv) {
        try {
            BigDecimal nav = mfNavService.getLatestNav(inv.getSchemeCode());
            return inv.getUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("NAV fetch failed for '{}' ({}): {}", inv.getName(), inv.getSchemeCode(), e.getMessage());
            return inv.getCurrentValue(); // keep last known value
        }
    }

    private BigDecimal recomputeFd(Investment inv) {
        if (inv.getInterestRate() == null || inv.getStartDate() == null) return inv.getCurrentValue();
        double rate = inv.getInterestRate().doubleValue();
        double years = ChronoUnit.DAYS.between(inv.getStartDate(), LocalDate.now()) / 365.25;
        // Quarterly compounding: P × (1 + r/4)^(4t)
        double value = inv.getInvestedAmount().doubleValue() * Math.pow(1 + rate / 4.0, 4.0 * years);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal recomputeRd(Investment inv) {
        if (inv.getInterestRate() == null || inv.getStartDate() == null) return inv.getCurrentValue();
        // Simple interest approximation: each rupee has been invested on average for half the elapsed period
        long monthsElapsed = ChronoUnit.MONTHS.between(inv.getStartDate(), LocalDate.now());
        double rate = inv.getInterestRate().doubleValue();
        double total = inv.getInvestedAmount().doubleValue();
        double interest = total * rate * monthsElapsed / 24.0; // /2 for average hold, /12 for annual→monthly
        return BigDecimal.valueOf(total + interest).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal recomputeEpf(Investment inv) {
        if (inv.getStartDate() == null) return inv.getCurrentValue();
        BigDecimal rate = inv.getInterestRate() != null ? inv.getInterestRate() : DEFAULT_EPF_RATE;
        double years = ChronoUnit.DAYS.between(inv.getStartDate(), LocalDate.now()) / 365.25;
        BigDecimal factor = BigDecimal.ONE.add(rate).pow((int) Math.floor(years * 12), MathContext.DECIMAL128);
        return inv.getInvestedAmount().multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private void postOpeningEntry(CreateInvestmentRequest req, UUID investmentAccountId, LocalDate date) {
        if (req.bankAccountId() != null) {
            accountingService.record(new RecordTransactionRequest(
                    date, "Investment — " + req.name(), EntryType.TRANSFER, null,
                    List.of(
                            new LineRequest(investmentAccountId, req.amount(), EntrySide.DEBIT, null),
                            new LineRequest(req.bankAccountId(), req.amount(), EntrySide.CREDIT, null))));
        } else {
            // EPF existing balance: single-sided opening — use OPENING_BALANCE to establish account
            accountingService.record(new RecordTransactionRequest(
                    date, "Opening balance — " + req.name(), EntryType.OPENING_BALANCE, null,
                    List.of(new LineRequest(investmentAccountId, req.amount(), EntrySide.DEBIT, null),
                            new LineRequest(investmentAccountId, req.amount(), EntrySide.CREDIT, "Opening"))));
        }
    }

    private void postSingleSidedContribution(Investment inv, BigDecimal amount) {
        // No bank account: record as adjustment to investment account balance only
        accountingService.record(new RecordTransactionRequest(
                LocalDate.now(), "Contribution — " + inv.getName(), EntryType.ADJUSTMENT, inv.getId(),
                List.of(new LineRequest(inv.getAccount().getId(), amount, EntrySide.DEBIT, "Contribution"),
                        new LineRequest(inv.getAccount().getId(), amount, EntrySide.CREDIT, "Contribution offset"))));
    }

    private Investment requireInvestment(UUID id) {
        return investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investment", id));
    }
}
