package com.moksh.walletwizzard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpcomingPaymentDto(
        String type,
        UUID referenceId,
        String name,
        LocalDate dueDate,
        BigDecimal amount,
        String detail
) {}
