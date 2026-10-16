package com.dungphd.insuranceass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class InsuranceAssApplication {

    public static void main(String[] args) {
        // Nạp file .env (nếu có) thành system properties TRƯỚC khi Spring đọc config.
        // Thứ tự ưu tiên: OS env ($env:) > .env file > giá trị mặc định trong application.properties
        try {
            io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(e -> {
                if (System.getenv(e.getKey()) == null && System.getProperty(e.getKey()) == null) {
                    System.setProperty(e.getKey(), e.getValue());
                }
            });
        } catch (Exception e) {
            System.out.println("[Dotenv] Không nạp được .env: " + e.getMessage());
        }

        // Fix Windows 8.3 short-path ownership issue with Spring Boot temp dir
        File tmpDir = new File(System.getProperty("user.dir"), "target/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        System.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());

        SpringApplication.run(InsuranceAssApplication.class, args);
    }
}
