package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Investment;
import com.moksh.walletwizzard.enums.InvestmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    @Query("""
            SELECT i FROM Investment i
            LEFT JOIN FETCH i.account
            ORDER BY i.type, i.name
            """)
    List<Investment> findAllWithAccount();

    @Query("""
            SELECT i FROM Investment i
            LEFT JOIN FETCH i.account
            WHERE i.type = :type
            ORDER BY i.name
            """)
    List<Investment> findByTypeWithAccount(InvestmentType type);
}
