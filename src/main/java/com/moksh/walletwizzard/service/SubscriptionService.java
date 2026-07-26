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
import com.moksh.walletwizzard.enums.RecurringSide;
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
        Account categoryRef = entityManager.getReference(Account.class, request.categoryAccountId());

        Subscription sub = Subscription.builder()
                .user(userRef)
                .name(request.name())
                .amount(request.amount())
                .billingCycle(request.billingCycle())
                .nextBillingDate(request.nextBillingDate())
                .side(request.side())
                .scheduleType(request.scheduleType())
                .paymentAccount(paymentRef)
                .categoryAccount(categoryRef)
                .notes(request.notes())
                .build();

        log.info("Creating recurring transaction '{}' side={} schedule={}", request.name(), request.side(), request.scheduleType());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void recordPayment(UUID subscriptionId, LocalDate paymentDate) {
        Subscription sub = subscriptionRepository.findByIdWithAccounts(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot record payment for a " + sub.getStatus() + " recurring transaction.");
        }
        if (sub.getPaymentAccount() == null || sub.getCategoryAccount() == null) {
            throw new IllegalStateException(
                    "Recurring transaction '" + sub.getName() + "' has no payment or category account configured.");
        }

        List<LineRequest> lines;
        if (sub.getSide() == RecurringSide.DEBIT) {
            // Outgoing: expense debited, payment account credited
            lines = List.of(
                    new LineRequest(sub.getCategoryAccount().getId(), sub.getAmount(), EntrySide.DEBIT, sub.getName()),
                    new LineRequest(sub.getPaymentAccount().getId(), sub.getAmount(), EntrySide.CREDIT, null)
            );
        } else {
            // Incoming: payment account debited (money arrives), income account credited
            lines = List.of(
                    new LineRequest(sub.getPaymentAccount().getId(), sub.getAmount(), EntrySide.DEBIT, null),
                    new LineRequest(sub.getCategoryAccount().getId(), sub.getAmount(), EntrySide.CREDIT, sub.getName())
            );
        }

        accountingService.record(new RecordTransactionRequest(
                paymentDate,
                "Recurring — " + sub.getName(),
                EntryType.SUBSCRIPTION,
                sub.getId(),
                lines));

        LocalDate base = sub.getNextBillingDate() != null ? sub.getNextBillingDate() : paymentDate;
        LocalDate nextDate = sub.getScheduleType().applyTo(sub.getBillingCycle().advance(base));
        sub.setNextBillingDate(nextDate);
        log.info("Recorded recurring payment for '{}', next date: {}", sub.getName(), nextDate);
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
        log.info("Updating recurring transaction {} status to {}", id, status);
        sub.setStatus(status);
        return sub;
    }
}
