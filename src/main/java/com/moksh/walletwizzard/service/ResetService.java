package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.init.DefaultAccountSeeder;
import com.moksh.walletwizzard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetService {

    private final EntityManager em;
    private final UserRepository userRepository;
    private final DefaultAccountSeeder accountSeeder;

    @Transactional
    public void resetUserData() {
        UUID userId = TenantContext.getCurrentUser();

        // loan_installments references journal_entries — must go before journal tables
        deleteWhere("loan_installments", userId);
        deleteWhere("loan_participants", userId);

        deleteWhere("group_expense_splits", userId);
        deleteWhere("group_expenses", userId);
        deleteWhere("group_members", userId);
        deleteWhere("expense_groups", userId);

        deleteWhere("account_participants", userId);
        deleteWhere("debt_records", userId);
        deleteWhere("loans", userId);
        deleteWhere("investments", userId);
        deleteWhere("subscriptions", userId);

        // journal_entry_tags has no user_id — scope via subquery
        deleteWhere("journal_entry_tags", userId,
                "journal_entry_id IN (SELECT id FROM journal_entries WHERE user_id = :uid)");
        deleteWhere("journal_entry_lines", userId);
        deleteWhere("journal_entries", userId);

        deleteWhere("tags", userId);
        deleteWhere("people", userId);
        deleteWhere("accounts", userId);

        log.info("Reset all financial data for user {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found after reset"));
        accountSeeder.seedDefaultAccounts(user);

        log.info("Re-seeded default accounts for user {}", userId);
    }

    private void deleteWhere(String table, UUID userId) {
        deleteWhere(table, userId, "user_id = :uid");
    }

    private void deleteWhere(String table, UUID userId, String condition) {
        int rows = em.createNativeQuery("DELETE FROM " + table + " WHERE " + condition)
                .setParameter("uid", userId)
                .executeUpdate();
        log.debug("Deleted {} rows from {}", rows, table);
    }
}
