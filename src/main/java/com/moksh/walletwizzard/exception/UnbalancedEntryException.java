package com.moksh.walletwizzard.exception;

import java.math.BigDecimal;

public class UnbalancedEntryException extends RuntimeException {

    public UnbalancedEntryException(BigDecimal debits, BigDecimal credits) {
        super(String.format(
                "Journal entry is unbalanced: total debits = %s, total credits = %s. They must be equal.",
                debits.toPlainString(), credits.toPlainString()
        ));
    }
}
