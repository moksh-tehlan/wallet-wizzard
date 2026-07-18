package com.moksh.walletwizzard.service;

import com.moksh.walletwizzard.dto.UserRegistrationRequest;
import com.moksh.walletwizzard.entity.User;
import com.moksh.walletwizzard.init.DefaultAccountSeeder;
import com.moksh.walletwizzard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DefaultAccountSeeder accountSeeder;

    /**
     * Atomically inserts the user if they don't exist (ON CONFLICT DO NOTHING),
     * then loads and returns the managed entity. Safe under concurrent first-login
     * requests — whichever thread wins the DB race, both end up with the same row.
     */
    @Transactional
    public User findOrCreate(UserRegistrationRequest request) {
        userRepository.insertIfAbsent(request.userId(), request.email(), request.name());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert — this should never happen"));
        accountSeeder.seedDefaultAccounts(user);
        return user;
    }
}
