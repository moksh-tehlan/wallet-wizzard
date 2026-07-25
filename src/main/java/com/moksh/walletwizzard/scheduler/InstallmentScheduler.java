package com.moksh.walletwizzard.scheduler;

import com.moksh.walletwizzard.service.LoanScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstallmentScheduler {

    private final LoanScheduleService loanScheduleService;

    /** Runs daily at 00:05 to mark installments whose due date has arrived. */
    @Scheduled(cron = "0 5 0 * * *")
    public void markDueInstallments() {
        int count = loanScheduleService.markInstallmentsDue(LocalDate.now());
        if (count > 0) {
            log.info("Daily scheduler: {} installment(s) marked DUE", count);
        }
    }
}
