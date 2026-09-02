package com.accuenergy.octopus.mgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OctopusMgmtApplication {
    public static void main(String[] args) {
        SpringApplication.run(OctopusMgmtApplication.class, args);
    }
}
