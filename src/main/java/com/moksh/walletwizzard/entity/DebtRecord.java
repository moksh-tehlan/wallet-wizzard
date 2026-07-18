package com.moksh.walletwizzard.entity;

import com.moksh.walletwizzard.enums.DebtDirection;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "debt_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtRecord extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    /**
     * The Receivable (ASSET) or Payable (LIABILITY) account created for this debt.
     * Its current balance is the live outstanding amount — no separate status field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DebtDirection direction;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal originalAmount;

    private String description;

    private LocalDate dueDate;

    @Version
    @Builder.Default
    private Long version = 0L;
}
