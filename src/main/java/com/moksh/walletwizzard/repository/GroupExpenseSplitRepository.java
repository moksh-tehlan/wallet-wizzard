package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.GroupExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GroupExpenseSplitRepository extends JpaRepository<GroupExpenseSplit, UUID> {

    @Query("SELECT s FROM GroupExpenseSplit s LEFT JOIN FETCH s.person WHERE s.expense.id = :expenseId ORDER BY s.createdAt")
    List<GroupExpenseSplit> findByExpenseId(UUID expenseId);

    /**
     * Unsettled splits for a specific person in a group.
     * Covers: expense paid by user → person has non-null person_id split.
     */
    @Query("""
            SELECT s FROM GroupExpenseSplit s
            JOIN s.expense e
            WHERE e.group.id = :groupId
              AND s.person.id = :personId
              AND s.isSettled = false
            ORDER BY e.date, s.createdAt
            """)
    List<GroupExpenseSplit> findUnsettledSplitsOwedByPerson(UUID groupId, UUID personId);

    /**
     * Unsettled owner-splits for expenses paid by a specific person (user owes them).
     * person_id IS NULL on the split means it's the owner's share.
     */
    @Query("""
            SELECT s FROM GroupExpenseSplit s
            JOIN s.expense e
            WHERE e.group.id = :groupId
              AND e.paidByPerson.id = :personId
              AND s.person IS NULL
              AND s.isSettled = false
            ORDER BY e.date, s.createdAt
            """)
    List<GroupExpenseSplit> findUnsettledSplitsOwedToPerson(UUID groupId, UUID personId);
}
