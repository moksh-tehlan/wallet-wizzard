package com.moksh.walletwizzard.init;

import com.moksh.walletwizzard.entity.Account;
import com.moksh.walletwizzard.enums.AccountSubType;
import com.moksh.walletwizzard.enums.AccountType;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultAccountSeeder {

    private final AccountRepository accountRepository;

    @Transactional
    public void seedDefaultAccounts(User user) {
        if (accountRepository.existsByIsSystemTrue()) {
            return;
        }

        // ── Group accounts (no subType — they ARE the groups) ─────────────────
        Account cashGroup   = group(user, "Cash & Equivalents",    AccountType.ASSET);
        Account bankGroup   = group(user, "Bank Accounts",          AccountType.ASSET);
        Account receivables = group(user, "Receivables",            AccountType.ASSET);
        Account creditCards = group(user, "Credit Cards",           AccountType.LIABILITY);
        Account loansGroup  = group(user, "Loans",                  AccountType.LIABILITY);
        Account equity      = group(user, "Opening Balance Equity", AccountType.EQUITY);

        List<Account> parents = accountRepository.saveAll(List.of(
                cashGroup, bankGroup, receivables, creditCards, loansGroup, equity
        ));
        cashGroup   = parents.get(0);
        bankGroup   = parents.get(1);
        receivables = parents.get(2);
        creditCards = parents.get(3);
        loansGroup  = parents.get(4);
        equity      = parents.get(5);

        // ── Leaf accounts (with subType and parent) ───────────────────────────
        List<Account> leaves = new ArrayList<>();

        leaves.add(leaf(user, "Cash",                    AccountType.ASSET,     AccountSubType.CASH_AND_WALLET, cashGroup));
        leaves.add(leaf(user, "Bank Account — Checking", AccountType.ASSET,     AccountSubType.BANK_ACCOUNT,    bankGroup));
        leaves.add(leaf(user, "Bank Account — Savings",  AccountType.ASSET,     AccountSubType.BANK_ACCOUNT,    bankGroup));

        leaves.add(leaf(user, "Credit Card",             AccountType.LIABILITY, AccountSubType.CREDIT_CARD,     creditCards));

        leaves.add(leaf(user, "Salary",                  AccountType.INCOME,    AccountSubType.INCOME_ACCOUNT,  null));
        leaves.add(leaf(user, "Freelance Income",        AccountType.INCOME,    AccountSubType.INCOME_ACCOUNT,  null));
        leaves.add(leaf(user, "Interest Income",         AccountType.INCOME,    AccountSubType.INCOME_ACCOUNT,  null));
        leaves.add(leaf(user, "Other Income",            AccountType.INCOME,    AccountSubType.INCOME_ACCOUNT,  null));

        leaves.add(leaf(user, "Food & Dining",           AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Transportation",          AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Housing & Rent",          AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Utilities",               AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Entertainment",           AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Healthcare",              AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Shopping",                AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Subscriptions",           AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Education",               AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Loan Interest",           AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));
        leaves.add(leaf(user, "Other Expense",           AccountType.EXPENSE,   AccountSubType.EXPENSE_ACCOUNT, null));

        accountRepository.saveAll(leaves);
    }

    private Account group(User user, String name, AccountType type) {
        return Account.builder()
                .user(user).name(name).type(type)
                .normalBalance(type.normalBalance())
                .isSystem(true)
                .build();
    }

    private Account leaf(User user, String name, AccountType type, AccountSubType subType, Account parent) {
        return Account.builder()
                .user(user).name(name).type(type)
                .normalBalance(type.normalBalance())
                .subType(subType)
                .parent(parent)
                .isSystem(true)
                .build();
    }
}
