package com.vivek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FkBlitzApplication {
    public static void main(String[] args) {
        SpringApplication.run(FkBlitzApplication.class, args);
    }
}
