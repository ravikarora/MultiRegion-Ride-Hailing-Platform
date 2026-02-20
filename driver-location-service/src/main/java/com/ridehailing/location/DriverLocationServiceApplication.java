package com.ridehailing.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriverLocationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverLocationServiceApplication.class, args);
    }
}
