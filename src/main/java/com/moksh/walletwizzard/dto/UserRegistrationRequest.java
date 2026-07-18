package com.moksh.walletwizzard.dto;

import java.util.UUID;

public record UserRegistrationRequest(UUID userId, String email, String name) {}
