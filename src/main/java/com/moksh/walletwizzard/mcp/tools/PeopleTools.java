package com.moksh.walletwizzard.mcp.tools;

import tools.jackson.databind.ObjectMapper;
import com.moksh.walletwizzard.dto.CreatePersonRequest;
import com.moksh.walletwizzard.entity.Person;
import com.moksh.walletwizzard.mcp.McpInputs;
import com.moksh.walletwizzard.mcp.dto.PersonView;
import com.moksh.walletwizzard.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PeopleTools {

    private final PersonService personService;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "add_person",
            description = "Adds a contact (person) used for tracking debts and loans.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String addPerson(
            @McpArg(name = "name", description = "Full name of the person", required = true) String name,
            @McpArg(name = "phone", description = "Phone number (optional)", required = false) String phone,
            @McpArg(name = "email", description = "Email address (optional)", required = false) String email,
            @McpArg(name = "notes", description = "Additional notes (optional)", required = false) String notes
    ) {
        Person person = personService.createPerson(new CreatePersonRequest(name, phone, email, notes));
        return "Person added: id=" + person.getId() + ", name=" + person.getName();
    }

    @McpTool(
            name = "list_people",
            description = "Lists all contacts (people) used for debt and loan tracking.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true)
    )
    public String listPeople() {
        List<Person> people = personService.listPeople();
        return objectMapper.writeValueAsString(people.stream()
                .map(p -> new PersonView(p.getId().toString(), p.getName(), p.getPhone(), p.getEmail()))
                .toList());
    }

    @McpTool(
            name = "update_person",
            description = "Updates a contact's name, phone, email, or notes.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, idempotentHint = true)
    )
    public String updatePerson(
            @McpArg(name = "personId", description = "UUID of the person to update", required = true) String personId,
            @McpArg(name = "name", description = "Updated name", required = true) String name,
            @McpArg(name = "phone", description = "Updated phone (optional)", required = false) String phone,
            @McpArg(name = "email", description = "Updated email (optional)", required = false) String email,
            @McpArg(name = "notes", description = "Updated notes (optional)", required = false) String notes
    ) {
        var person = personService.update(McpInputs.requireUuid(personId, "personId"), new CreatePersonRequest(name, phone, email, notes));
        return "Person updated: id=" + person.getId() + ", name=" + person.getName();
    }
}
