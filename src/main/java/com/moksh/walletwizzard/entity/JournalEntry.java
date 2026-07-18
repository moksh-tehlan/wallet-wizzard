package com.moksh.walletwizzard.entity;

import com.moksh.walletwizzard.enums.EntryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryType entryType;

    /**
     * Back-reference to the originating domain object (subscription id, loan id, debt record id).
     * Null for manually created entries. Allows querying "all journal entries for loan #X".
     */
    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Version
    @Builder.Default
    private Long version = 0L;

    /**
     * The double-entry lines that make up this transaction.
     * Always LAZY — use JOIN FETCH in repository queries when lines are needed.
     * CascadeType.ALL + orphanRemoval: lines are owned by the entry and are
     * never reassigned to a different entry.
     */
    @OneToMany(
            mappedBy = "journalEntry",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @Builder.Default
    private List<JournalEntryLine> lines = new ArrayList<>();
}
