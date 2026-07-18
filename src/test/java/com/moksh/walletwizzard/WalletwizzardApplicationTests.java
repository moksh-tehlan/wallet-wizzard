package com.moksh.walletwizzard;

import com.moksh.walletwizzard.service.AccountingService;
import com.moksh.walletwizzard.enums.EntrySide;
import com.moksh.walletwizzard.enums.EntryType;
import com.moksh.walletwizzard.dto.LineRequest;
import com.moksh.walletwizzard.dto.RecordTransactionRequest;
import com.moksh.walletwizzard.exception.UnbalancedEntryException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for core domain invariants.
 *
 * Full integration tests (context load, Flyway migrations, RLS enforcement)
 * require a running PostgreSQL instance and live in src/test/java/...integration/.
 * Run them with: SPRING_PROFILES_ACTIVE=local ./gradlew integrationTest
 */
class WalletwizzardApplicationTests {

    /**
     * The double-entry balance invariant is the most critical rule in the system.
     * Verify it is enforced at the Java layer (before the DB trigger fires).
     */
    @Test
    void unbalancedEntryIsRejectedByAccountingService() {
        // We test the validation logic directly without a Spring context or DB.
        // AccountingService's validate() is private, so we exercise it via record().
        // The service will throw before touching any repository.
        var service = new AccountingService(null, null);

        var unbalancedRequest = new RecordTransactionRequest(
                LocalDate.now(),
                "Test",
                EntryType.EXPENSE,
                null,
                List.of(
                        new LineRequest(UUID.randomUUID(), new BigDecimal("100.00"), EntrySide.DEBIT, null),
                        new LineRequest(UUID.randomUUID(), new BigDecimal("90.00"), EntrySide.CREDIT, null) // ≠ 100
                )
        );

        assertThrows(UnbalancedEntryException.class, () -> service.record(unbalancedRequest),
                "AccountingService must reject entries where SUM(DEBIT) ≠ SUM(CREDIT)");
    }

    @Test
    void entryWithFewerThanTwoLinesIsRejected() {
        var service = new AccountingService(null, null);

        var tooFewLines = new RecordTransactionRequest(
                LocalDate.now(), "Test", EntryType.EXPENSE, null,
                List.of(new LineRequest(UUID.randomUUID(), new BigDecimal("100.00"), EntrySide.DEBIT, null))
        );

        assertThrows(IllegalArgumentException.class, () -> service.record(tooFewLines));
    }

    @Test
    void billingCycleAdvancesCorrectly() {
        var base = LocalDate.of(2026, 1, 31);
        // MONTHLY from Jan 31 → Feb 28 (not Feb 31, which doesn't exist)
        var next = com.moksh.walletwizzard.enums.BillingCycle.MONTHLY.advance(base);
        assertTrue(next.getMonthValue() == 2, "Monthly advance from Jan should land in February");
        assertTrue(next.getDayOfMonth() == 28, "Should cap at last day of February");
    }
}
