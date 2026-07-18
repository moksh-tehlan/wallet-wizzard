package com.moksh.walletwizzard.entity;

import com.moksh.walletwizzard.enums.EntrySide;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One leg of a double-entry journal entry.
 * Lines are immutable once created — no @Version, no updatedAt, no Auditable.
 * To correct an error, reverse the original entry and record a new one.
 */
@Entity
@Table(name = "journal_entry_lines")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryLine {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Redundant FK carried for RLS efficiency (avoids a subquery join on every access).
     * Must always match journalEntry.user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /** Always positive — direction is expressed by side (DEBIT / CREDIT). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntrySide side;

    private String memo;
}
