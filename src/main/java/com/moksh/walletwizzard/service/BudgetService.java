package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.Budget;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.reporting.dto.BudgetStatusDto;
import com.moksh.walletwizzard.repository.BudgetRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final AccountService accountService;
    private final EntityManager entityManager;

    @Transactional
    public Budget setBudget(UUID accountId, BigDecimal amount) {
        // upsert: update if exists, create if not
        return budgetRepository.findByAccountIdAndPeriod(accountId, "MONTHLY")
                .map(existing -> {
                    existing.setAmount(amount);
                    log.info("Updated budget for account {} to {}", accountId, amount);
                    return budgetRepository.save(existing);
                })
                .orElseGet(() -> {
                    User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
                    Account accountRef = entityManager.getReference(Account.class, accountId);
                    Budget budget = Budget.builder()
                            .user(userRef)
                            .account(accountRef)
                            .amount(amount)
                            .period("MONTHLY")
                            .build();
                    log.info("Created budget for account {} amount {}", accountId, amount);
                    return budgetRepository.save(budget);
                });
    }

    public List<BudgetStatusDto> getBudgetStatus(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<Budget> budgets = budgetRepository.findAllByPeriodWithAccount("MONTHLY");

        return budgets.stream().map(b -> {
            BigDecimal spent = accountService.getSpendForAccount(b.getAccount().getId(), from, to);
            return BudgetStatusDto.of(b.getId(), b.getAccount().getId(),
                    b.getAccount().getName(), b.getAmount(), spent);
        }).toList();
    }

    @Transactional
    public void deleteBudget(UUID budgetId) {
        if (!budgetRepository.existsById(budgetId)) {
            throw new ResourceNotFoundException("Budget", budgetId);
        }
        budgetRepository.deleteById(budgetId);
        log.info("Deleted budget {}", budgetId);
    }
}
