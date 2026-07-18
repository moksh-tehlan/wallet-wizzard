package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Subscription;
import com.moksh.walletwizzard.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("""
            SELECT s FROM Subscription s
            LEFT JOIN FETCH s.paymentAccount
            LEFT JOIN FETCH s.expenseAccount
            WHERE s.status = :status
            ORDER BY s.name
            """)
    List<Subscription> findByStatusWithAccounts(SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
            LEFT JOIN FETCH s.paymentAccount
            LEFT JOIN FETCH s.expenseAccount
            ORDER BY s.name
            """)
    List<Subscription> findAllWithAccounts();

    /** Active subscriptions due within the next N days, ordered by due date. */
    @Query("""
            SELECT s FROM Subscription s
            LEFT JOIN FETCH s.paymentAccount
            LEFT JOIN FETCH s.expenseAccount
            WHERE s.status = 'ACTIVE'
              AND s.nextBillingDate IS NOT NULL
              AND s.nextBillingDate <= :cutoff
            ORDER BY s.nextBillingDate
            """)
    List<Subscription> findUpcomingBills(@Param("cutoff") LocalDate cutoff);

    @Query("""
            SELECT s FROM Subscription s
            LEFT JOIN FETCH s.paymentAccount
            LEFT JOIN FETCH s.expenseAccount
            WHERE s.id = :id
            """)
    Optional<Subscription> findByIdWithAccounts(@Param("id") UUID id);
}
