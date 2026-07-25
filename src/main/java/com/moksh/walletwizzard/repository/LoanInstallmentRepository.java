package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.LoanInstallment;
import com.moksh.walletwizzard.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, UUID> {

    @Query("SELECT i FROM LoanInstallment i JOIN FETCH i.loan WHERE i.loan.id = :loanId ORDER BY i.installmentNo")
    List<LoanInstallment> findByLoanIdOrderByInstallmentNo(UUID loanId);

    boolean existsByLoanId(UUID loanId);

    @Query("""
            SELECT i FROM LoanInstallment i JOIN FETCH i.loan
            WHERE i.status = 'SCHEDULED' AND i.dueDate <= :today
            """)
    List<LoanInstallment> findScheduledInstallmentsDueBy(LocalDate today);

    @Query("""
            SELECT i FROM LoanInstallment i JOIN FETCH i.loan
            WHERE i.status IN ('SCHEDULED', 'DUE') AND i.dueDate <= :before
            ORDER BY i.dueDate
            """)
    List<LoanInstallment> findUpcomingInstallments(LocalDate before);

    @Query("""
            SELECT i FROM LoanInstallment i
            WHERE i.loan.id = :loanId AND i.status IN ('DUE', 'PAID')
            """)
    List<LoanInstallment> findPaidAndDueByLoanId(UUID loanId);

    List<LoanInstallment> findByLoanIdAndStatus(UUID loanId, InstallmentStatus status);
}
