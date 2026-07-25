package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.person WHERE gm.group.id = :groupId ORDER BY gm.createdAt")
    List<GroupMember> findByGroupIdWithPerson(UUID groupId);

    boolean existsByGroupIdAndPersonId(UUID groupId, UUID personId);

    int countByGroupId(UUID groupId);
}
