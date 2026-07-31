package com.moksh.walletwizzard.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.ok().build();
    }
}
