package com.stays.rate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.stays")
public class RateApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateApplication.class, args);
    }
}
