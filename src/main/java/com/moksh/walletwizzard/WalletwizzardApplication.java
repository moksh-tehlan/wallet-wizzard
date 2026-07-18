package com.moksh.walletwizzard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan
public class WalletwizzardApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletwizzardApplication.class, args);
    }
}
