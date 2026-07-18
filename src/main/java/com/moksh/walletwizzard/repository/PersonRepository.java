package com.moksh.walletwizzard.repository;

import com.moksh.walletwizzard.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {
}
