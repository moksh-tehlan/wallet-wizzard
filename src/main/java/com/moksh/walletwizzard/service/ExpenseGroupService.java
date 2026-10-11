package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.*;
import com.moksh.walletwizzard.entity.*;
import com.moksh.walletwizzard.enums.*;
import com.moksh.walletwizzard.exception.InvalidInputException;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExpenseGroupService {

    private final ExpenseGroupRepository groupRepo;
    private final GroupMemberRepository memberRepo;
    private final GroupExpenseRepository expenseRepo;
    private final GroupExpenseSplitRepository splitRepo;
    private final PersonService personService;
    private final AccountService accountService;
    private final AccountingService accountingService;
    private final EntityManager entityManager;

    // ─────────────────────────────────────────────────────────────────────────
    // Create group
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ExpenseGroup createGroup(String name, String notes, List<UUID> personIds) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Group name is required.");
        }

        User userRef = userRef();
        ExpenseGroup group = groupRepo.save(ExpenseGroup.builder()
                .user(userRef)
                .name(name.strip())
                .notes(notes)
                .build());

        for (UUID personId : personIds) {
            addMemberInternal(group, personId, userRef);
        }

        log.info("Created expense group '{}' with {} members", name, personIds.size());
        return group;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add member
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GroupMember addMember(UUID groupId, UUID personId) {
        ExpenseGroup group = requireGroup(groupId);
        if (memberRepo.existsByGroupIdAndPersonId(groupId, personId)) {
            Person p = personService.getById(personId);
            throw new InvalidInputException(
                    p.getName() + " is already a member of group '" + group.getName() + "'.");
        }
        return addMemberInternal(group, personId, userRef());
    }

    private GroupMember addMemberInternal(ExpenseGroup group, UUID personId, User userRef) {
        Person person = personService.getById(personId);
        return memberRepo.save(GroupMember.builder()
                .user(userRef)
                .group(group)
                .person(person)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add expense
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GroupExpenseDto addExpense(UUID groupId, String description, BigDecimal totalAmount,
                                     UUID paidByPersonId, UUID expenseAccountId, UUID bankAccountId,
                                     LocalDate date) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Expense amount must be greater than zero.");
        }

        ExpenseGroup group = requireGroup(groupId);
        List<GroupMember> members = memberRepo.findByGroupIdWithPerson(groupId);
        if (members.isEmpty()) {
            throw new InvalidInputException(
                    "Group '" + group.getName() + "' has no members. Add members before adding expenses.");
        }

        LocalDate expenseDate = date != null ? date : LocalDate.now();
        User userRef = userRef();

        Person paidByPerson = paidByPersonId != null ? personService.getById(paidByPersonId) : null;

        // Build and save the expense
        GroupExpense expense = GroupExpense.builder()
                .user(userRef)
                .group(group)
                .description(description)
                .totalAmount(totalAmount)
                .paidByPerson(paidByPerson)
                .expenseAccount(expenseAccountId != null ? accountRef(expenseAccountId) : null)
                .bankAccount(bankAccountId != null ? accountRef(bankAccountId) : null)
                .date(expenseDate)
                .splitType(SplitType.EQUAL)
                .build();

        // EQUAL split: totalMembers = non-owner members + 1 (owner)
        int totalMembers = members.size() + 1;
        BigDecimal sharePerMember = totalAmount.divide(
                BigDecimal.valueOf(totalMembers), 4, RoundingMode.DOWN);
        // Last member absorbs rounding so splits sum to totalAmount exactly
        BigDecimal lastShare = totalAmount.subtract(sharePerMember.multiply(BigDecimal.valueOf(totalMembers - 1)));

        if (paidByPerson == null) {
            // Owner paid → post journal entry, create splits for each non-owner member
            if (expenseAccountId != null && bankAccountId != null) {
                var je = accountingService.record(new RecordTransactionRequest(
                        expenseDate, description, EntryType.EXPENSE, null,
                        List.of(
                                new LineRequest(expenseAccountId, totalAmount, EntrySide.DEBIT, null),
                                new LineRequest(bankAccountId, totalAmount, EntrySide.CREDIT, null)
                        ), null
                ));
                expense.setJournalEntryId(je.getId());
            }

            expense = expenseRepo.save(expense);

            List<GroupExpenseSplit> splits = new ArrayList<>(members.size());
            for (int i = 0; i < members.size(); i++) {
                BigDecimal share = (i == members.size() - 1) ? lastShare : sharePerMember;
                splits.add(GroupExpenseSplit.builder()
                        .user(userRef)
                        .expense(expense)
                        .person(members.get(i).getPerson())
                        .shareAmount(share)
                        .build());
            }
            splitRepo.saveAll(splits);
            log.info("Added group expense '{}' ₹{} paid by owner in group '{}'",
                    description, totalAmount, group.getName());
        } else {
            // Someone else paid → track only the owner's share as what user owes that person
            expense = expenseRepo.save(expense);

            // Find the last slot in the equal split for the owner's share
            // Owner is member index 0 conceptually; their share is sharePerMember (or lastShare if only 1 other)
            BigDecimal ownerShare = sharePerMember; // owner's equal slice
            splitRepo.save(GroupExpenseSplit.builder()
                    .user(userRef)
                    .expense(expense)
                    .person(null) // null = owner's share
                    .shareAmount(ownerShare)
                    .build());
            log.info("Added group expense '{}' ₹{} paid by {} in group '{}'",
                    description, totalAmount, paidByPerson.getName(), group.getName());
        }

        return toExpenseDto(expense, splitRepo.findByExpenseId(expense.getId()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List groups / expenses
    // ─────────────────────────────────────────────────────────────────────────

    public List<GroupSummaryDto> listGroups() {
        return groupRepo.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(g -> new GroupSummaryDto(
                        g.getId(),
                        g.getName(),
                        memberRepo.countByGroupId(g.getId()),
                        expenseRepo.sumTotalAmountByGroupId(g.getId()),
                        g.getNotes()))
                .toList();
    }

    public List<GroupExpenseDto> listGroupExpenses(UUID groupId) {
        requireGroup(groupId);
        return expenseRepo.findByGroupIdOrderByDateDesc(groupId).stream()
                .map(e -> toExpenseDto(e, splitRepo.findByExpenseId(e.getId())))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Balance
    // ─────────────────────────────────────────────────────────────────────────

    public GroupBalanceDto getGroupBalance(UUID groupId) {
        ExpenseGroup group = requireGroup(groupId);
        List<GroupMember> members = memberRepo.findByGroupIdWithPerson(groupId);

        List<GroupBalanceDto.MemberBalance> balances = members.stream()
                .map(m -> {
                    UUID personId = m.getPerson().getId();
                    String personName = m.getPerson().getName();

                    BigDecimal theyOweYou = splitRepo
                            .findUnsettledSplitsOwedByPerson(groupId, personId).stream()
                            .map(GroupExpenseSplit::getShareAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal youOweThem = splitRepo
                            .findUnsettledSplitsOwedToPerson(groupId, personId).stream()
                            .map(GroupExpenseSplit::getShareAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal net = theyOweYou.subtract(youOweThem);
                    return new GroupBalanceDto.MemberBalance(personId, personName, theyOweYou, youOweThem, net);
                })
                .toList();

        return new GroupBalanceDto(groupId, group.getName(), balances);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete expense / group
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public String deleteGroupExpense(UUID expenseId) {
        GroupExpense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Group expense", expenseId));
        if (expense.getJournalEntryId() != null) {
            accountingService.voidEntry(expense.getJournalEntryId());
        }
        splitRepo.deleteByExpenseId(expenseId);
        expenseRepo.deleteById(expenseId);
        log.info("Deleted group expense '{}' ({})", expense.getDescription(), expenseId);
        return "Deleted expense '" + expense.getDescription() + "' (₹" + expense.getTotalAmount().toPlainString() + ")."
                + (expense.getJournalEntryId() != null ? " Journal entry voided." : "");
    }

    @Transactional
    public String deleteGroup(UUID groupId) {
        ExpenseGroup group = requireGroup(groupId);
        List<GroupExpense> expenses = expenseRepo.findByGroupIdOrderByDateDesc(groupId);
        int voided = 0;
        for (GroupExpense expense : expenses) {
            if (expense.getJournalEntryId() != null) {
                accountingService.voidEntry(expense.getJournalEntryId());
                voided++;
            }
            splitRepo.deleteByExpenseId(expense.getId());
        }
        expenseRepo.deleteAll(expenses);
        memberRepo.deleteByGroupId(groupId);
        group.setActive(false);
        groupRepo.save(group);
        log.info("Deleted group '{}' ({}) — {} expenses removed, {} journal entries voided",
                group.getName(), groupId, expenses.size(), voided);
        return "Group '" + group.getName() + "' deleted. "
                + expenses.size() + " expenses removed, " + voided + " journal entries voided.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simplified settlement
    // ─────────────────────────────────────────────────────────────────────────

    public GroupSettlementDto getSimplifiedSettlement(UUID groupId) {
        ExpenseGroup group = requireGroup(groupId);
        List<GroupMember> members = memberRepo.findByGroupIdWithPerson(groupId);
        List<GroupExpense> expenses = expenseRepo.findByGroupIdOrderByDateDesc(groupId);

        int totalParticipants = members.size() + 1; // members + owner

        // null key = owner; personId key = that member
        Map<UUID, BigDecimal> net = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        net.put(null, BigDecimal.ZERO);
        names.put(null, "You");
        for (GroupMember m : members) {
            net.put(m.getPerson().getId(), BigDecimal.ZERO);
            names.put(m.getPerson().getId(), m.getPerson().getName());
        }
        Set<UUID> memberIds = members.stream()
                .map(m -> m.getPerson().getId())
                .collect(Collectors.toSet());

        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (GroupExpense expense : expenses) {
            totalExpenses = totalExpenses.add(expense.getTotalAmount());
            List<GroupExpenseSplit> splits = splitRepo.findByExpenseId(expense.getId());

            BigDecimal perShare = expense.getTotalAmount()
                    .divide(BigDecimal.valueOf(totalParticipants), 4, RoundingMode.HALF_UP);

            if (expense.getPaidByPerson() == null) {
                // Owner paid: each non-settled member split is a debt to owner
                for (GroupExpenseSplit split : splits) {
                    if (!split.isSettled() && split.getPerson() != null) {
                        UUID memberId = split.getPerson().getId();
                        net.merge(null, split.getShareAmount(), BigDecimal::add);
                        net.merge(memberId, split.getShareAmount().negate(), BigDecimal::add);
                    }
                }
            } else {
                UUID payerId = expense.getPaidByPerson().getId();

                // Owner's share (stored split with person=null)
                BigDecimal ownerShare = splits.stream()
                        .filter(s -> !s.isSettled() && s.getPerson() == null)
                        .map(GroupExpenseSplit::getShareAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (ownerShare.compareTo(BigDecimal.ZERO) > 0) {
                    net.merge(payerId, ownerShare, BigDecimal::add);
                    net.merge(null, ownerShare.negate(), BigDecimal::add);
                }

                // Cross-member: each non-payer member owes payer perShare
                // (not stored in DB, always treated as unsettled)
                for (UUID memberId : memberIds) {
                    if (!memberId.equals(payerId)) {
                        net.merge(payerId, perShare, BigDecimal::add);
                        net.merge(memberId, perShare.negate(), BigDecimal::add);
                    }
                }
            }
        }

        // Greedy debt simplification: match largest debtor with largest creditor
        Map<UUID, BigDecimal> balances = new HashMap<>(net);
        BigDecimal threshold = new BigDecimal("0.01");
        List<GroupSettlementDto.Payment> settlements = new ArrayList<>();

        while (true) {
            Optional<Map.Entry<UUID, BigDecimal>> maxCreditor = balances.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(threshold) > 0)
                    .max(Map.Entry.comparingByValue());
            Optional<Map.Entry<UUID, BigDecimal>> maxDebtor = balances.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(threshold.negate()) < 0)
                    .min(Map.Entry.comparingByValue());

            if (maxCreditor.isEmpty() || maxDebtor.isEmpty()) break;

            UUID creditorId = maxCreditor.get().getKey();
            UUID debtorId = maxDebtor.get().getKey();
            BigDecimal credit = balances.get(creditorId);
            BigDecimal debt = balances.get(debtorId).abs();
            BigDecimal payment = credit.min(debt).setScale(2, RoundingMode.HALF_UP);

            settlements.add(new GroupSettlementDto.Payment(
                    names.get(debtorId), debtorId,
                    names.get(creditorId), creditorId,
                    payment));

            balances.merge(creditorId, payment.negate(), BigDecimal::add);
            balances.merge(debtorId, payment, BigDecimal::add);
        }

        BigDecimal perPersonShare = totalParticipants > 0
                ? totalExpenses.divide(BigDecimal.valueOf(totalParticipants), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new GroupSettlementDto(groupId, group.getName(), totalExpenses, perPersonShare, settlements);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settle
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GroupBalanceDto settleGroupMember(UUID groupId, UUID personId,
                                             UUID bankAccountId, BigDecimal amount,
                                             LocalDate date) {
        ExpenseGroup group = requireGroup(groupId);
        Person person = personService.getById(personId);

        // Determine what's outstanding
        List<GroupExpenseSplit> owedByPerson =
                splitRepo.findUnsettledSplitsOwedByPerson(groupId, personId);
        List<GroupExpenseSplit> owedToPerson =
                splitRepo.findUnsettledSplitsOwedToPerson(groupId, personId);

        BigDecimal totalOwedByPerson = owedByPerson.stream()
                .map(GroupExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOwedToPerson = owedToPerson.stream()
                .map(GroupExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal net = totalOwedByPerson.subtract(totalOwedToPerson);
        if (net.compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidInputException(
                    person.getName() + " has no outstanding balance in group '" + group.getName() + "'.");
        }

        BigDecimal settleAmount = amount != null ? amount : net.abs();
        if (settleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Settlement amount must be greater than zero.");
        }

        LocalDate settleDate = date != null ? date : LocalDate.now();

        // Post journal entry via "Group Reimbursements" income account
        Account reimbAccount = accountService.findOrCreateAccount(new CreateAccountRequest(
                "Group Reimbursements", AccountType.INCOME, AccountSubType.INCOME_ACCOUNT,
                null, "Auto-created for group expense settlements"));

        if (net.compareTo(BigDecimal.ZERO) > 0) {
            // Person owes user → user receives money: DEBIT bank, CREDIT reimbursements
            accountingService.record(new RecordTransactionRequest(
                    settleDate,
                    "Group settlement — " + person.getName() + " → " + group.getName(),
                    EntryType.GROUP_SETTLEMENT, null,
                    List.of(
                            new LineRequest(bankAccountId, settleAmount, EntrySide.DEBIT, "Received from " + person.getName()),
                            new LineRequest(reimbAccount.getId(), settleAmount, EntrySide.CREDIT, "Group reimbursement")
                    ), null
            ));
            markSettled(owedByPerson, settleAmount, settleDate);
        } else {
            // User owes person → user pays: DEBIT reimbursements, CREDIT bank
            accountingService.record(new RecordTransactionRequest(
                    settleDate,
                    "Group settlement — " + group.getName() + " → " + person.getName(),
                    EntryType.GROUP_SETTLEMENT, null,
                    List.of(
                            new LineRequest(reimbAccount.getId(), settleAmount, EntrySide.DEBIT, "Paid to " + person.getName()),
                            new LineRequest(bankAccountId, settleAmount, EntrySide.CREDIT, "Group settlement payment")
                    ), null
            ));
            markSettled(owedToPerson, settleAmount, settleDate);
        }

        log.info("Settled group balance with {} in group '{}': {}", person.getName(), group.getName(), settleAmount);
        return getGroupBalance(groupId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void markSettled(List<GroupExpenseSplit> splits, BigDecimal amount, LocalDate date) {
        BigDecimal remaining = amount;
        for (GroupExpenseSplit split : splits) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            split.setSettled(true);
            split.setSettledDate(date);
            remaining = remaining.subtract(split.getShareAmount());
        }
        splitRepo.saveAll(splits);
    }

    private ExpenseGroup requireGroup(UUID groupId) {
        return groupRepo.findByIdAndIsActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense group", groupId));
    }

    private User userRef() {
        return entityManager.getReference(User.class, TenantContext.getCurrentUser());
    }

    private Account accountRef(UUID accountId) {
        return entityManager.getReference(Account.class, accountId);
    }

    private GroupExpenseDto toExpenseDto(GroupExpense expense, List<GroupExpenseSplit> splits) {
        String paidBy = expense.getPaidByPerson() != null
                ? expense.getPaidByPerson().getName() : "You (owner)";

        List<GroupExpenseDto.SplitView> splitViews = splits.stream()
                .map(s -> new GroupExpenseDto.SplitView(
                        s.getPerson() != null ? s.getPerson().getId() : null,
                        s.getPerson() != null ? s.getPerson().getName() : "You (owner)",
                        s.getShareAmount(),
                        s.isSettled()))
                .toList();

        return new GroupExpenseDto(
                expense.getId(),
                expense.getDescription(),
                expense.getTotalAmount(),
                paidBy,
                expense.getDate(),
                splitViews);
    }
}
