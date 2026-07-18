package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    @Query("""
            SELECT l FROM Loan l
            JOIN FETCH l.account
            LEFT JOIN FETCH l.interestAccount
            ORDER BY l.createdAt DESC
            """)
    List<Loan> findAllWithAccounts();

    @Query("""
            SELECT l FROM Loan l
            JOIN FETCH l.account
            LEFT JOIN FETCH l.interestAccount
            WHERE l.id = :id
            """)
    Optional<Loan> findByIdWithAccounts(UUID id);
}
