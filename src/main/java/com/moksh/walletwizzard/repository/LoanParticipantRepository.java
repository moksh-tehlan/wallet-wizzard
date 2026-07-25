package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.LoanParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanParticipantRepository extends JpaRepository<LoanParticipant, UUID> {

    @Query("SELECT p FROM LoanParticipant p JOIN FETCH p.person WHERE p.loan.id = :loanId")
    List<LoanParticipant> findByLoanIdWithPerson(UUID loanId);

    @Query("SELECT p FROM LoanParticipant p JOIN FETCH p.loan WHERE p.person.id = :personId")
    List<LoanParticipant> findByPersonIdWithLoan(UUID personId);

    Optional<LoanParticipant> findByLoanIdAndPersonId(UUID loanId, UUID personId);

    boolean existsByLoanIdAndPersonId(UUID loanId, UUID personId);
}
