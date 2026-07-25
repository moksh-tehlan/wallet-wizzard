package com.moksh.walletwizzard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_participants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanParticipant extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    /** e.g. 50.00 = 50% share of each installment */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePercent;

    private String notes;
}
