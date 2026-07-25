package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, UUID> {

    List<ExpenseGroup> findByIsActiveTrueOrderByCreatedAtDesc();

    Optional<ExpenseGroup> findByIdAndIsActiveTrue(UUID id);
}
