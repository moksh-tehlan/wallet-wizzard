package com.moksh.walletwizzard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "account_participants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountParticipant extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    /** Their share as a percentage: 50.00 = 50%. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePercent;

    private String notes;

    @Version
    @Builder.Default
    private Long version = 0L;
}
