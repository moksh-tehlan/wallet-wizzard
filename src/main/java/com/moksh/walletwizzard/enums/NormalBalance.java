package com.moksh.walletwizzard.enums;

public enum NormalBalance {
    DEBIT, CREDIT;

    public NormalBalance opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
