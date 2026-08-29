package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.AccountShareSummaryDto;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.AccountParticipant;
import com.moksh.walletwizzard.entity.Person;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.exception.InvalidInputException;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.AccountParticipantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountParticipantService {

    private final AccountParticipantRepository repository;
    private final AccountService accountService;
    private final EntityManager entityManager;

    @Transactional
    public AccountParticipant addParticipant(UUID accountId, UUID personId,
                                             BigDecimal sharePercent, String notes) {
        if (sharePercent.compareTo(BigDecimal.ZERO) <= 0 || sharePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidInputException("sharePercent must be between 0 and 100, got: " + sharePercent);
        }
        if (repository.existsByAccountIdAndPersonId(accountId, personId)) {
            throw new InvalidInputException("Person is already a participant for this account.");
        }

        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        Account accountRef = entityManager.getReference(Account.class, accountId);
        Person personRef = entityManager.getReference(Person.class, personId);

        AccountParticipant participant = AccountParticipant.builder()
                .user(userRef)
                .account(accountRef)
                .person(personRef)
                .sharePercent(sharePercent)
                .notes(notes)
                .build();

        log.info("Added participant {} ({}%) to account {}", personId, sharePercent, accountId);
        return repository.save(participant);
    }

    public AccountShareSummaryDto getShareSummary(UUID accountId) {
        Account account = accountService.getById(accountId);
        List<AccountParticipant> participants = repository.findByAccountIdWithPerson(accountId);
        BigDecimal balance = accountService.getBalance(accountId);

        List<AccountShareSummaryDto.ParticipantShareView> views = participants.stream()
                .map(p -> {
                    BigDecimal share = balance.multiply(p.getSharePercent())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    return new AccountShareSummaryDto.ParticipantShareView(
                            p.getId(), p.getPerson().getId(), p.getPerson().getName(),
                            p.getSharePercent(), share, p.getNotes());
                })
                .toList();

        return new AccountShareSummaryDto(
                accountId, account.getName(), account.getType().name(), balance, views);
    }

    @Transactional
    public void removeParticipant(UUID accountId, UUID personId) {
        if (!repository.existsByAccountIdAndPersonId(accountId, personId)) {
            throw new ResourceNotFoundException("Participant not found for account " + accountId + " and person " + personId);
        }
        repository.deleteByAccountIdAndPersonId(accountId, personId);
        log.info("Removed participant {} from account {}", personId, accountId);
    }

    public List<AccountParticipant> getParticipantsForPerson(UUID personId) {
        return repository.findByPersonIdWithAccount(personId);
    }
}
