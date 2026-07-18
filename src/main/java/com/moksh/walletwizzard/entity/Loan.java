package com.moksh.walletwizzard.entity;

import com.moksh.walletwizzard.enums.LoanDirection;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** The Loan Payable (TAKEN) or Loan Receivable (GIVEN) account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /**
     * The expense (TAKEN) or income (GIVEN) account that absorbs interest.
     * Defaults to "Other Expense" / "Other Income" if not explicitly specified.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_account_id")
    private Account interestAccount;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LoanDirection direction;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal principal;

    /** Stored as decimal rate: 0.0875 = 8.75% per annum. */
    @Column(precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Column(precision = 18, scale = 2)
    private BigDecimal emiAmount;

    private LocalDate startDate;

    private LocalDate endDate;

    private String notes;
}
