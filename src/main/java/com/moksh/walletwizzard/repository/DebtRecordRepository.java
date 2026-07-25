package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.DebtRecord;
import com.moksh.walletwizzard.enums.DebtDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRecordRepository extends JpaRepository<DebtRecord, UUID> {

    @Query("SELECT dr FROM DebtRecord dr JOIN FETCH dr.person JOIN FETCH dr.account WHERE dr.id = :id")
    Optional<DebtRecord> findByIdWithDetails(UUID id);

    @Query("""
            SELECT dr FROM DebtRecord dr
            JOIN FETCH dr.person
            JOIN FETCH dr.account
            ORDER BY dr.createdAt DESC
            """)
    List<DebtRecord> findAllWithDetails();

    @Query("""
            SELECT dr FROM DebtRecord dr
            JOIN FETCH dr.person
            JOIN FETCH dr.account
            WHERE dr.direction = :direction
            ORDER BY dr.createdAt DESC
            """)
    List<DebtRecord> findByDirectionWithDetails(DebtDirection direction);

    @Query("""
            SELECT dr FROM DebtRecord dr
            JOIN FETCH dr.person
            JOIN FETCH dr.account
            WHERE dr.person.id = :personId
            ORDER BY dr.createdAt DESC
            """)
    List<DebtRecord> findByPersonWithDetails(UUID personId);
}
