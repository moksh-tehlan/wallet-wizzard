package com.moksh.walletwizzard.enums;

public enum AccountType {
    ASSET, LIABILITY, INCOME, EXPENSE, EQUITY;

    /** Returns the normal balance side for this account type per double-entry convention. */
    public NormalBalance normalBalance() {
        return switch (this) {
            case ASSET, EXPENSE -> NormalBalance.DEBIT;
            case LIABILITY, INCOME, EQUITY -> NormalBalance.CREDIT;
        };
    }
}
