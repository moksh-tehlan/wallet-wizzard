package com.moksh.walletwizzard.enums;

public enum AccountSubType {

    // ── ASSET ────────────────────────────────────────────────────────────────
    BANK_ACCOUNT("Bank Accounts"),
    CASH_AND_WALLET("Cash & Equivalents"),
    RECEIVABLE("Receivables"),
    OTHER_ASSET(null),

    // ── LIABILITY ────────────────────────────────────────────────────────────
    CREDIT_CARD("Credit Cards"),
    LOAN_PAYABLE("Loans"),
    OTHER_LIABILITY(null),

    // ── INCOME / EXPENSE ─────────────────────────────────────────────────────
    INCOME_ACCOUNT(null),
    EXPENSE_ACCOUNT(null);

    /** Name of the system group account that should be the parent, or null for no auto-parent. */
    public final String defaultParentName;

    AccountSubType(String defaultParentName) {
        this.defaultParentName = defaultParentName;
    }
}
