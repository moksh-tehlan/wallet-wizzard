package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Investment;
import com.moksh.walletwizzard.enums.InvestmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    @Query("""
            SELECT i FROM Investment i
            LEFT JOIN FETCH i.account
            ORDER BY i.type, i.name
            """)
    List<Investment> findAllWithAccount();

    @Query("""
            SELECT i FROM Investment i
            LEFT JOIN FETCH i.account
            WHERE i.type = :type
            ORDER BY i.name
            """)
    List<Investment> findByTypeWithAccount(InvestmentType type);

    @Query("""
            SELECT i FROM Investment i
            JOIN FETCH i.account
            JOIN FETCH i.defaultBankAccount
            WHERE i.defaultBankAccount IS NOT NULL
              AND i.nextContributionDate IS NOT NULL
              AND i.nextContributionDate <= :today
              AND i.monthlyContribution IS NOT NULL
            """)
    List<Investment> findDueContributions(LocalDate today);

    @Query("""
            SELECT i FROM Investment i
            LEFT JOIN FETCH i.account
            WHERE i.maturityDate IS NOT NULL
              AND i.maturityDate <= :cutoff
            ORDER BY i.maturityDate
            """)
    List<Investment> findUpcomingMaturities(LocalDate cutoff);
}
