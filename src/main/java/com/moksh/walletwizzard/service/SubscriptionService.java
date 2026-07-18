package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreateSubscriptionRequest;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.entity.Subscription;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.enums.SubscriptionStatus;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final AccountingService accountingService;
    private final EntityManager entityManager;

    @Transactional
    public Subscription createSubscription(@Valid CreateSubscriptionRequest request) {
        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        Account paymentRef = entityManager.getReference(Account.class, request.paymentAccountId());
        Account expenseRef = entityManager.getReference(Account.class, request.expenseAccountId());

        Subscription sub = Subscription.builder()
                .user(userRef)
                .name(request.name())
                .amount(request.amount())
                .billingCycle(request.billingCycle())
                .nextBillingDate(request.nextBillingDate())
                .paymentAccount(paymentRef)
                .expenseAccount(expenseRef)
                .notes(request.notes())
                .build();

        log.info("Creating subscription '{}'", request.name());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void recordPayment(UUID subscriptionId, LocalDate paymentDate) {
        Subscription sub = subscriptionRepository.findByIdWithAccounts(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot record payment for a " + sub.getStatus() + " subscription.");
        }
        if (sub.getPaymentAccount() == null || sub.getExpenseAccount() == null) {
            throw new IllegalStateException(
                    "Subscription '" + sub.getName() + "' has no payment or expense account configured.");
        }

        var lines = List.of(
                new LineRequest(sub.getExpenseAccount().getId(), sub.getAmount(), EntrySide.DEBIT, sub.getName()),
                new LineRequest(sub.getPaymentAccount().getId(), sub.getAmount(), EntrySide.CREDIT, null)
        );

        accountingService.record(new RecordTransactionRequest(
                paymentDate,
                "Subscription — " + sub.getName(),
                EntryType.SUBSCRIPTION,
                sub.getId(),
                lines));

        LocalDate nextDate = sub.getBillingCycle().advance(
                sub.getNextBillingDate() != null ? sub.getNextBillingDate() : paymentDate);
        sub.setNextBillingDate(nextDate);
        log.info("Recorded payment for subscription '{}', next billing: {}", sub.getName(), nextDate);
    }

    public List<Subscription> listSubscriptions(SubscriptionStatus status) {
        if (status != null) {
            return subscriptionRepository.findByStatusWithAccounts(status);
        }
        return subscriptionRepository.findAllWithAccounts();
    }

    public List<Subscription> getUpcomingBills(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return subscriptionRepository.findUpcomingBills(cutoff);
    }

    @Transactional
    public Subscription updateStatus(UUID id, SubscriptionStatus status) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
        log.info("Updating subscription {} status to {}", id, status);
        sub.setStatus(status);
        return sub;
    }
}
