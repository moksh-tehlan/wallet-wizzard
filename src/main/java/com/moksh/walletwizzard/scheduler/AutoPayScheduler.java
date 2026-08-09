package com.moksh.walletwizzard.scheduler;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.entity.Investment;
import com.moksh.walletwizzard.entity.LoanInstallment;
import com.moksh.walletwizzard.entity.Subscription;
import com.moksh.walletwizzard.repository.InvestmentRepository;
import com.moksh.walletwizzard.repository.LoanInstallmentRepository;
import com.moksh.walletwizzard.repository.SubscriptionRepository;
import com.moksh.walletwizzard.service.InvestmentService;
import com.moksh.walletwizzard.service.LoanScheduleService;
import com.moksh.walletwizzard.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPayScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final InvestmentRepository investmentRepository;

    private final SubscriptionService subscriptionService;
    private final LoanScheduleService loanScheduleService;
    private final InvestmentService investmentService;

    /** Runs daily at 06:00 (after the 00:05 mark-due job). */
    @Scheduled(cron = "0 0 6 * * *")
    public void runAutoPay() {
        LocalDate today = LocalDate.now();
        log.info("Auto-pay scheduler starting for {}", today);

        processSubscriptions(today);
        processLoanInstallments(today);
        processInvestmentContributions(today);

        log.info("Auto-pay scheduler completed for {}", today);
    }

    private void processSubscriptions(LocalDate today) {
        subscriptionRepository.findUpcomingBills(today).forEach(sub -> {
            try {
                withUser(sub.getUser().getId(), () ->
                        subscriptionService.recordPayment(sub.getId(), today));
                log.info("Auto-paid subscription '{}' ({})", sub.getName(), sub.getId());
            } catch (Exception e) {
                log.error("Auto-pay failed for subscription '{}' ({}): {}", sub.getName(), sub.getId(), e.getMessage());
            }
        });
    }

    private void processLoanInstallments(LocalDate today) {
        installmentRepository.findDueForAutoPay(today).forEach(installment -> {
            try {
                withUser(installment.getUser().getId(), () ->
                        loanScheduleService.payInstallment(
                                installment.getId(),
                                installment.getLoan().getDefaultBankAccount().getId(),
                                today));
                log.info("Auto-paid installment #{} for loan '{}' ({})",
                        installment.getInstallmentNo(),
                        installment.getLoan().getName(),
                        installment.getId());
            } catch (Exception e) {
                log.error("Auto-pay failed for installment #{} of loan '{}' ({}): {}",
                        installment.getInstallmentNo(),
                        installment.getLoan().getName(),
                        installment.getId(),
                        e.getMessage());
            }
        });
    }

    private void processInvestmentContributions(LocalDate today) {
        investmentRepository.findDueContributions(today).forEach(investment -> {
            try {
                withUser(investment.getUser().getId(), () ->
                        investmentService.recordContribution(
                                investment.getId(),
                                investment.getMonthlyContribution(),
                                null,
                                investment.getDefaultBankAccount().getId()));
                log.info("Auto-contributed {} to investment '{}' ({})",
                        investment.getMonthlyContribution(), investment.getName(), investment.getId());
            } catch (Exception e) {
                log.error("Auto-contribute failed for investment '{}' ({}): {}",
                        investment.getName(), investment.getId(), e.getMessage());
            }
        });
    }

    private void withUser(java.util.UUID userId, Runnable task) {
        try {
            TenantContext.setCurrentUser(userId);
            task.run();
        } finally {
            TenantContext.clear();
        }
    }
}
