package com.moksh.walletwizzard.dto;

import com.moksh.walletwizzard.enums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentDto(
        UUID id,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal totalAmount,
        InstallmentStatus status,
        LocalDate paidDate
) {}
