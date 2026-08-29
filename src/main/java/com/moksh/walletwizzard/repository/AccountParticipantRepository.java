package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.AccountParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AccountParticipantRepository extends JpaRepository<AccountParticipant, UUID> {

    @Query("SELECT p FROM AccountParticipant p JOIN FETCH p.person WHERE p.account.id = :accountId")
    List<AccountParticipant> findByAccountIdWithPerson(UUID accountId);

    @Query("SELECT p FROM AccountParticipant p JOIN FETCH p.account WHERE p.person.id = :personId")
    List<AccountParticipant> findByPersonIdWithAccount(UUID personId);

    boolean existsByAccountIdAndPersonId(UUID accountId, UUID personId);

    void deleteByAccountIdAndPersonId(UUID accountId, UUID personId);
}
