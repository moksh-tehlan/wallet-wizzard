package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.JournalEntry;
import com.moksh.walletwizzard.enums.EntryType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /**
     * Fetches entries with their lines in a single query (JOIN FETCH avoids N+1).
     * RLS scopes results to the current user automatically.
     */
    @Query("""
            SELECT DISTINCT je FROM JournalEntry je
            LEFT JOIN FETCH je.lines l
            LEFT JOIN FETCH l.account
            WHERE (:from IS NULL OR je.date >= :from)
              AND (:to   IS NULL OR je.date <= :to)
              AND (:entryType IS NULL OR je.entryType = :entryType)
            ORDER BY je.date DESC, je.createdAt DESC
            """)
    List<JournalEntry> findByFiltersWithLines(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("entryType") EntryType entryType,
            Pageable pageable
    );

    /**
     * Entries that touch a specific account, with lines pre-fetched.
     */
    @Query("""
            SELECT DISTINCT je FROM JournalEntry je
            JOIN je.lines l
            LEFT JOIN FETCH je.lines fl
            LEFT JOIN FETCH fl.account
            WHERE l.account.id = :accountId
              AND (:from IS NULL OR je.date >= :from)
              AND (:to   IS NULL OR je.date <= :to)
            ORDER BY je.date DESC, je.createdAt DESC
            """)
    List<JournalEntry> findByAccountWithLines(
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    /** Single entry with its lines — used for detail view. */
    @Query("""
            SELECT je FROM JournalEntry je
            LEFT JOIN FETCH je.lines l
            LEFT JOIN FETCH l.account
            WHERE je.id = :id
            """)
    Optional<JournalEntry> findByIdWithLines(@Param("id") UUID id);

    /** All entries referencing a domain object (e.g. all payments for loan #X). */
    @Query("""
            SELECT je FROM JournalEntry je
            LEFT JOIN FETCH je.lines l
            LEFT JOIN FETCH l.account
            WHERE je.referenceId = :referenceId
            ORDER BY je.date DESC
            """)
    List<JournalEntry> findByReferenceIdWithLines(@Param("referenceId") UUID referenceId);

    boolean existsByReferenceIdAndEntryType(@Param("referenceId") UUID referenceId,
                                            @Param("entryType") com.moksh.walletwizzard.enums.EntryType entryType);
}
