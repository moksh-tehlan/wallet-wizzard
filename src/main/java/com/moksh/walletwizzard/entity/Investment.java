package com.moksh.walletwizzard.entity;

import com.moksh.walletwizzard.enums.InvestmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "investments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** Auto-created ASSET account — balance tracks cost basis (cash flows only). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentType type;

    @Column(nullable = false)
    private String name;

    /** Total cash invested (cost basis). Updated on each contribution. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal investedAmount;

    /** Latest market/computed value. Updated on refresh. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal currentValue;

    /** MF only: units held. */
    @Column(precision = 18, scale = 6)
    private BigDecimal units;

    /** MF only: mfapi.in scheme code (e.g. "120503"). */
    private String schemeCode;

    /** EPF/FD/RD: annual interest rate as decimal (0.0825 = 8.25%). */
    @Column(precision = 7, scale = 4)
    private BigDecimal interestRate;

    /** EPF/RD: monthly contribution amount. */
    @Column(precision = 18, scale = 2)
    private BigDecimal monthlyContribution;

    private LocalDate startDate;

    /** FD/RD: maturity date. */
    private LocalDate maturityDate;

    /** When currentValue was last refreshed. */
    private LocalDate lastRefreshedAt;

    private String notes;

    @Version
    @Builder.Default
    private Long version = 0L;
}
