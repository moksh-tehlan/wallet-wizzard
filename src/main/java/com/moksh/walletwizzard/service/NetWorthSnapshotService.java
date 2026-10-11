package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.entity.NetWorthSnapshot;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.reporting.ReportingService;
import com.moksh.walletwizzard.reporting.dto.LiquidNetWorthDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthDto;
import com.moksh.walletwizzard.reporting.dto.NetWorthHistoryDto;
import com.moksh.walletwizzard.repository.NetWorthSnapshotRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NetWorthSnapshotService {

    private final NetWorthSnapshotRepository snapshotRepository;
    private final ReportingService reportingService;
    private final EntityManager entityManager;

    @Transactional
    public NetWorthSnapshot recordSnapshot(UUID userId) {
        LocalDate today = LocalDate.now();
        if (snapshotRepository.existsBySnapshotDate(today)) {
            // update today's snapshot if it already exists
            return snapshotRepository.findBySnapshotDate(today).map(existing -> {
                updateSnapshotValues(existing);
                return snapshotRepository.save(existing);
            }).orElseThrow();
        }

        NetWorthDto nw = reportingService.getNetWorth(today);
        LiquidNetWorthDto lnw = reportingService.getLiquidNetWorth();

        User userRef = entityManager.getReference(User.class, userId);
        NetWorthSnapshot snapshot = NetWorthSnapshot.builder()
                .user(userRef)
                .snapshotDate(today)
                .totalAssets(nw.totalAssets())
                .totalLiabilities(nw.totalLiabilities())
                .netWorth(nw.netWorth())
                .liquidNetWorth(lnw.liquidNetWorth())
                .build();

        log.info("Recorded net worth snapshot for {}: netWorth={}, liquid={}", today, nw.netWorth(), lnw.liquidNetWorth());
        return snapshotRepository.save(snapshot);
    }

    public NetWorthHistoryDto getHistory(int months) {
        LocalDate from = LocalDate.now().minusMonths(months).withDayOfMonth(1);
        List<NetWorthSnapshot> snapshots = snapshotRepository.findFromDate(from);

        List<NetWorthHistoryDto.SnapshotPoint> points = snapshots.stream()
                .map(s -> new NetWorthHistoryDto.SnapshotPoint(
                        s.getSnapshotDate().toString(),
                        s.getNetWorth(),
                        s.getLiquidNetWorth()))
                .toList();

        BigDecimal change = BigDecimal.ZERO;
        double changePct = 0.0;
        if (points.size() >= 2) {
            BigDecimal oldest = snapshots.getFirst().getNetWorth();
            BigDecimal latest = snapshots.getLast().getNetWorth();
            change = latest.subtract(oldest);
            if (oldest.compareTo(BigDecimal.ZERO) != 0) {
                changePct = change.multiply(BigDecimal.valueOf(100))
                        .divide(oldest.abs(), 2, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }

        return new NetWorthHistoryDto(points, change, changePct);
    }

    private void updateSnapshotValues(NetWorthSnapshot snapshot) {
        NetWorthDto nw = reportingService.getNetWorth(snapshot.getSnapshotDate());
        LiquidNetWorthDto lnw = reportingService.getLiquidNetWorth();
        snapshot.setTotalAssets(nw.totalAssets());
        snapshot.setTotalLiabilities(nw.totalLiabilities());
        snapshot.setNetWorth(nw.netWorth());
        snapshot.setLiquidNetWorth(lnw.liquidNetWorth());
    }
}
