package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.config.TenantContext;
import com.moksh.walletwizzard.dto.CreatePersonRequest;
import com.moksh.walletwizzard.entity.Person;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.exception.ResourceNotFoundException;
import com.moksh.walletwizzard.repository.PersonRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PersonService {

    private final PersonRepository personRepository;
    private final EntityManager entityManager;

    @Transactional
    public Person createPerson(@Valid CreatePersonRequest request) {
        User userRef = entityManager.getReference(User.class, TenantContext.getCurrentUser());
        Person person = Person.builder()
                .user(userRef)
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .notes(request.notes())
                .build();
        log.info("Creating person '{}'", request.name());
        return personRepository.save(person);
    }

    public List<Person> listPeople() {
        return personRepository.findAll();
    }

    public Person getById(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", id));
    }

    @Transactional
    public Person update(UUID id, @Valid CreatePersonRequest request) {
        Person person = getById(id);
        person.setName(request.name());
        person.setPhone(request.phone());
        person.setEmail(request.email());
        person.setNotes(request.notes());
        return person;
    }
}
