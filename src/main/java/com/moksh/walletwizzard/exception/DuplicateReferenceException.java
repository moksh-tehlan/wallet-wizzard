package com.moksh.walletwizzard.exception;

import java.util.UUID;

public class DuplicateReferenceException extends RuntimeException {

    public DuplicateReferenceException(UUID referenceId) {
        super("A transaction with referenceId " + referenceId + " already exists. "
                + "Use get_transactions to retrieve the existing entry.");
    }
}
