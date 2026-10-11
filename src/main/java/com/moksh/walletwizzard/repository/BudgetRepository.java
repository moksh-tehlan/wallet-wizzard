package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query("SELECT b FROM Budget b JOIN FETCH b.account WHERE b.period = :period ORDER BY b.account.name")
    List<Budget> findAllByPeriodWithAccount(String period);

    @Query("SELECT b FROM Budget b JOIN FETCH b.account WHERE b.account.id = :accountId AND b.period = :period")
    Optional<Budget> findByAccountIdAndPeriod(UUID accountId, String period);

    boolean existsByAccountIdAndPeriod(UUID accountId, String period);
}
