package com.uniforex.apexdata;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // This tells Spring we want to use background timers later
public class Main {

    public static void main(String[] args) {

        System.out.println("Starting ApexData Spring Boot Server...");

        // 3. Launch Spring Boot
        SpringApplication.run(Main.class, args);
    }
}