package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.NetWorthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, UUID> {

    boolean existsBySnapshotDate(LocalDate date);

    @Query("SELECT s FROM NetWorthSnapshot s WHERE s.snapshotDate >= :from ORDER BY s.snapshotDate ASC")
    List<NetWorthSnapshot> findFromDate(LocalDate from);

    Optional<NetWorthSnapshot> findBySnapshotDate(LocalDate date);
}
