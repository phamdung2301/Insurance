package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("!test")
    public CommandLineRunner initData() {
        return args -> {
            try {
                // 1. Seed Users
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

                // 2. Seed Sample Policy with Nested Locations
                if (policyRepository.count() == 0) {
                    log.info("Initializing sample policies in MongoDB...");

                    Coverage fireCoverage = Coverage.builder()
                            .coverageCode("FIRE-01")
                            .coverageName("Fire & Lightning Protection")
                            .coverageType("STANDARD")
                            .limit(500000000.0)
                            .deductible(10000000.0)
                            .termMonths(12)
                            .baseRate(0.0015)
                            .premium(750000.0)
                            .build();

                    Coverage waterCoverage = Coverage.builder()
                            .coverageCode("WATER-02")
                            .coverageName("Water Damage & Flood")
                            .coverageType("ENHANCED")
                            .limit(200000000.0)
                            .deductible(5000000.0)
                            .termMonths(12)
                            .baseRate(0.001)
                            .premium(200000.0)
                            .build();

                    List<Coverage> loc1Coverages = new ArrayList<>();
                    loc1Coverages.add(fireCoverage);
                    loc1Coverages.add(waterCoverage);

                    Location loc1 = Location.builder()
                            .locationId(1)
                            .address("123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                            .coverages(loc1Coverages)
                            .build();

                    Location loc2 = Location.builder()
                            .locationId(2)
                            .address("789 Đường Võ Văn Kiệt, Quận 5, TP. Hồ Chí Minh")
                            .coverages(new ArrayList<>(List.of(fireCoverage)))
                            .build();

                    List<Location> initialLocations = new ArrayList<>();
                    initialLocations.add(loc1);
                    initialLocations.add(loc2);

                    Insured insured = Insured.builder()
                            .insuredId("INS-001")
                            .name("Công ty TNHH Giải Pháp Công Nghệ Toàn Cầu")
                            .type("BUSINESS")
                            .email("contact@globalsolutions.vn")
                            .phone("02838229988")
                            .address("123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh")
                            .build();

                    Policy samplePolicy = Policy.builder()
                            .policyNumber("POL-2026-001")
                            .status(PolicyStatus.DRAFT)
                            .insured(insured)
                            .locations(initialLocations)
                            .effectiveDate(Instant.now())
                            .expirationDate(Instant.now().plus(365, ChronoUnit.DAYS))
                            .version(1)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    samplePolicy.recalculateTotalPremium();
                    policyRepository.save(samplePolicy);
                    log.info("Sample policy created: POL-2026-001 with totalPremium = {}", samplePolicy.getTotalPremium());
                }
            } catch (Exception e) {
                log.warn("Could not initialize sample data (MongoDB may be offline): {}", e.getMessage());
            }
        };
    }
}
