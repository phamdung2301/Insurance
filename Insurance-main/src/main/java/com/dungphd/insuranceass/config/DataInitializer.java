package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("!test")
    public CommandLineRunner initUsers() {
        return args -> {
            try {
                if (userRepository.count() == 0) {
                    log.info("Initializing sample users in MongoDB...");

                    User defaultUser = User.builder()
                            .email("user@example.com")
                            .password(passwordEncoder.encode("Password123!"))
                            .fullName("Nguyễn Văn An")
                            .phone("0987654321")
                            .address("123 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                            .roles(List.of("ROLE_USER"))
                            .enabled(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    User adminUser = User.builder()
                            .email("admin@example.com")
                            .password(passwordEncoder.encode("Admin123!"))
                            .fullName("Trần Thị Quản Trị")
                            .phone("0912345678")
                            .address("456 Đường Nam Kỳ Khởi Nghĩa, Quận 3, TP. Hồ Chí Minh")
                            .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                            .enabled(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    userRepository.saveAll(List.of(defaultUser, adminUser));
                    log.info("Sample users created successfully! (user@example.com / Password123!)");
                }
            } catch (Exception e) {
                log.warn("Could not initialize sample users (MongoDB may be offline): {}", e.getMessage());
            }
        };
    }
}
