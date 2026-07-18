package com.moksh.walletwizzard.enums;

public enum LoanDirection {
    /** You took the loan — creates a Loan Payable (LIABILITY) account. */
    TAKEN,
    /** You gave the loan — creates a Loan Receivable (ASSET) account. */
    GIVEN
}
