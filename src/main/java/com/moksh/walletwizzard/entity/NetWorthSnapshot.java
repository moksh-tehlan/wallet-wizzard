package com.moksh.walletwizzard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "net_worth_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetWorthSnapshot extends Auditable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAssets;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netWorth;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal liquidNetWorth;
}
