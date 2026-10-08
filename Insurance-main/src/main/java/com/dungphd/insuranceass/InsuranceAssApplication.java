package com.dungphd.insuranceass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class InsuranceAssApplication {

    public static void main(String[] args) {
        // Fix Windows 8.3 short-path ownership issue with Spring Boot temp dir
        File tmpDir = new File(System.getProperty("user.dir"), "target/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        System.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());

        SpringApplication.run(InsuranceAssApplication.class, args);
    }
}
