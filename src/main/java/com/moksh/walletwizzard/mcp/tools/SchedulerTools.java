package com.moksh.walletwizzard.mcp.tools;

import com.moksh.walletwizzard.scheduler.AutoPayScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchedulerTools {

    private final AutoPayScheduler autoPayScheduler;

    @McpTool(
            name = "trigger_auto_pay",
            description = """
                    Manually runs the auto-pay scheduler right now (same logic as the daily 6AM job).
                    Processes:
                    - Recurring subscriptions due today or earlier
                    - Loan EMIs due today or earlier (only if set_loan_auto_pay was configured)
                    - RD/SIP contributions due today or earlier (only if set_investment_auto_contribute was configured)
                    Safe to call multiple times — nextBillingDate/nextContributionDate advance after each run,
                    so already-processed items are skipped. Use for testing or to manually trigger a missed run.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, idempotentHint = false)
    )
    public String triggerAutoPay() {
        autoPayScheduler.runAutoPay();
        return "Auto-pay run completed. Check server logs for details on what was processed.";
    }
}
