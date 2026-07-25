package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.GroupExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface GroupExpenseRepository extends JpaRepository<GroupExpense, UUID> {

    @Query("SELECT ge FROM GroupExpense ge LEFT JOIN FETCH ge.paidByPerson WHERE ge.group.id = :groupId ORDER BY ge.date DESC, ge.createdAt DESC")
    List<GroupExpense> findByGroupIdOrderByDateDesc(UUID groupId);

    @Query("SELECT COALESCE(SUM(ge.totalAmount), 0) FROM GroupExpense ge WHERE ge.group.id = :groupId")
    BigDecimal sumTotalAmountByGroupId(UUID groupId);
}
