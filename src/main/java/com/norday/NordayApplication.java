package com.norday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class NordayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NordayApplication.class, args);
    }

    @PostConstruct
    public void configurarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
    }

}
