package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByIsActiveTrue();

    List<Account> findAllByTypeAndIsActiveTrue(AccountType type);

    List<Account> findAllByParentId(UUID parentId);

    boolean existsByIsSystemTrue();

    Optional<Account> findByNameAndIsSystemTrueAndIsActiveTrue(String name);

    Optional<Account> findByNameAndIsActiveTrue(String name);

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
            ), 0)
            FROM journal_entry_lines jel
            JOIN accounts a ON jel.account_id = a.id
            WHERE jel.account_id = :accountId
            """, nativeQuery = true)
    BigDecimal computeBalance(@Param("accountId") UUID accountId);

    /** Batch balance query — one SQL round-trip for many accounts, avoids N+1 in list operations. */
    @Query(value = """
            SELECT jel.account_id::text, COALESCE(SUM(
                CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
            ), 0)
            FROM journal_entry_lines jel
            JOIN accounts a ON jel.account_id = a.id
            WHERE jel.account_id IN :accountIds
            GROUP BY jel.account_id
            """, nativeQuery = true)
    List<Object[]> computeBalancesForAccounts(@Param("accountIds") List<UUID> accountIds);

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN jel.side = a.normal_balance THEN jel.amount ELSE -jel.amount END
            ), 0)
            FROM journal_entry_lines jel
            JOIN accounts a ON jel.account_id = a.id
            JOIN journal_entries je ON jel.journal_entry_id = je.id
            WHERE jel.account_id = :accountId
              AND je.date <= :asOfDate
            """, nativeQuery = true)
    BigDecimal computeBalanceAsOf(@Param("accountId") UUID accountId,
                                  @Param("asOfDate") LocalDate asOfDate);
}
