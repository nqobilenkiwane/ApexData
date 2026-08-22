package com.uniforex.apexdata;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // This tells Spring we want to use background timers later
public class Main {

    public static void main(String[] args) {
        // 1. Load the .env file
        Dotenv dotenv = Dotenv.load();

        // 2. Push .env variables into System Properties so application.properties can see them
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        System.out.println("Starting ApexData Spring Boot Server...");

        // 3. Launch Spring Boot
        SpringApplication.run(Main.class, args);
    }
}