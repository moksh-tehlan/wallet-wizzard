package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Atomic upsert — safe under concurrent first-login requests.
     * ON CONFLICT DO NOTHING means whichever thread wins the race inserts; the other
     * is a no-op. Both threads then re-read the committed row via findById.
     */
    @Modifying
    @Query(value = """
            INSERT INTO users (id, email, name, currency, version, created_at, updated_at)
            VALUES (:id, :email, :name, 'INR', 0, NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id,
                        @Param("email") String email,
                        @Param("name") String name);
}
