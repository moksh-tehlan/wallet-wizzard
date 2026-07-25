package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreateAccountRequest;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.exception.InvalidInputException;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.AccountRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    @Transactional
    public Account findOrCreateAccount(CreateAccountRequest request) {
        return accountRepository.findByNameAndIsActiveTrue(request.name())
                .orElseGet(() -> createAccount(request));
    }

    @Transactional
    public Account createAccount(@Valid CreateAccountRequest request) {
        if (accountRepository.findByNameAndIsActiveTrue(request.name()).isPresent()) {
            throw new InvalidInputException(
                    "An account named '" + request.name() + "' already exists. "
                    + "Use list_accounts to find it, or choose a different name.");
        }
        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());

        Account account = Account.builder()
                .user(userRef)
                .name(request.name())
                .type(request.type())
                .normalBalance(request.type().normalBalance())
                .subType(request.subType())
                .notes(request.notes())
                .build();

        if (request.parentId() != null) {
            account.setParent(entityManager.getReference(Account.class, request.parentId()));
        } else if (request.subType() != null && request.subType().defaultParentName != null) {
            account.setParent(resolveSystemGroup(request.subType()));
        }

        log.info("Creating account '{}' of type {} subType {}", request.name(), request.type(), request.subType());
        return accountRepository.save(account);
    }

    private Account resolveSystemGroup(AccountSubType subType) {
        return accountRepository.findByNameAndIsSystemTrueAndIsActiveTrue(subType.defaultParentName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "System group account '" + subType.defaultParentName + "' not found. "
                        + "Make sure the server has completed its initial setup."));
    }

    public List<Account> listAccounts(AccountType type) {
        if (type != null) {
            return accountRepository.findAllByTypeAndIsActiveTrue(type);
        }
        return accountRepository.findAllByIsActiveTrue();
    }

    public List<Account> listSubAccounts(UUID parentId) {
        return accountRepository.findAllByParentId(parentId);
    }

    public Account getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    public BigDecimal getBalance(UUID accountId) {
        return accountRepository.computeBalance(accountId);
    }

    public BigDecimal getBalanceAsOf(UUID accountId, LocalDate asOfDate) {
        return accountRepository.computeBalanceAsOf(accountId, asOfDate);
    }

    public Map<UUID, BigDecimal> getBalancesForAccounts(List<UUID> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        return accountRepository.computeBalancesForAccounts(accountIds).stream()
                .collect(Collectors.toMap(
                        row -> UUID.fromString((String) row[0]),
                        row -> (BigDecimal) row[1]
                ));
    }

    @Transactional
    public Account rename(UUID id, String newName) {
        Account account = getById(id);
        log.info("Renaming account {} to '{}'", id, newName);
        account.setName(newName);
        return account;
    }

    @Transactional
    public Account deactivate(UUID id) {
        Account account = getById(id);
        if (account.isSystem()) {
            throw new IllegalStateException("System accounts cannot be deactivated.");
        }
        log.info("Deactivating account {}", id);
        account.setActive(false);
        return account;
    }
}
