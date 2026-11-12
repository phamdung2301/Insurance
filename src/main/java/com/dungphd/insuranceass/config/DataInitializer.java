package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final PolicyVersionRepository policyVersionRepository;
    private final PolicyTransactionRepository policyTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgentRepository agentRepository;

    /**
     * Công tắc seed dữ liệu demo (app.seed.demo-data, env SEED_DEMO_DATA).
     * Mặc định true cho dev/demo vì các tài khoản mẫu có mật khẩu công khai
     * trong repo — deploy thật PHẢI đặt SEED_DEMO_DATA=false.
     */
    @Value("${app.seed.demo-data:true}")
    private boolean seedDemoData;

    /**
     * Mã cố định của agent seed — SaaS Phase 1: test không phụ thuộc thứ tự
     * sinh mã của AgentCodeGenerator (counter), và chạy lại seed không tạo
     * thêm agent (find-by-code trước khi insert).
     */
    static final String SEED_AGENT_CODE = "AGT-SEED";
    static final String SEED_AGENT_EMAIL = "agent@example.com";

    @Bean
    @Profile("!test")
    public CommandLineRunner initData() {
        return args -> {
            if (!seedDemoData) {
                log.info("Demo data seeding is disabled (app.seed.demo-data=false).");
                return;
            }
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

                // SaaS Phase 1 — THỨ TỰ BẮT BUỘC: seed Agent TRƯỚC rồi mới gán
                // agentId cho agent@example.com (PLAN §2.1). Agent seed dùng mã
                // cố định AGT-SEED để test không phụ thuộc thứ tự sinh mã.
                Agent seedAgent = seedAgent();

                // Seed 4 tài khoản demo theo role (PLAN §3): find-or-create THEO TỪNG
                // EMAIL, NGOÀI cổng count() == 0 — DB đã có dữ liệu thì seed mới
                // vẫn chạy. User đã tồn tại thì giữ nguyên, không sửa password/roles.
                seedDemoUser("customer@example.com", "Password123!", "Khách Hàng Mẫu", List.of("ROLE_CUSTOMER"));
                seedDemoUser("agent@example.com", "Password123!", "Đại Lý Mẫu", List.of("ROLE_AGENT"));
                assignSeedAgentToDemoAgent(seedAgent);
                seedDemoUser("uw@example.com", "Password123!", "Thẩm Định Viên Mẫu", List.of("ROLE_UNDERWRITER"));
                seedDemoUser("finance@example.com", "Password123!", "Kế Toán Mẫu", List.of("ROLE_FINANCE"));
                // admin@example.com giữ nguyên như seed gốc (ROLE_ADMIN, ROLE_USER).

                // Tài khoản demo kế toán từng seed sai ROLE_ADMIN (trước PR
                // finance-scope): DB đã có user thì seedDemoUser giữ nguyên
                // nên phải sửa một lần ở đây — idempotent, chỉ chạm đúng
                // finance@example.com khi nó vẫn mang ROLE_ADMIN cũ.
                repairFinanceDemoUser();

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
                            .phone("0903822998")
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
                    Policy saved = policyRepository.save(samplePolicy);
                    log.info("Sample policy created: POL-2026-001 with totalPremium = {}", saved.getTotalPremium());

                    PolicyVersion v1 = PolicyVersion.builder()
                            .policyNumber(saved.getPolicyNumber())
                            .version(1)
                            .policySnapshot(saved)
                            .endorsementReason("Initial Policy Baseline (v1)")
                            .endorsementType("BASELINE")
                            .endorsementEffectiveDate(saved.getEffectiveDate())
                            .premiumChange(0.0)
                            .totalPremium(saved.getTotalPremium())
                            .status(saved.getStatus())
                            .createdBy("System")
                            .createdAt(saved.getCreatedAt())
                            .build();
                    policyVersionRepository.save(v1);

                    PolicyTransaction txn = PolicyTransaction.builder()
                            .policyNumber(saved.getPolicyNumber())
                            .version(1)
                            .transactionType(TransactionType.CREATE_POLICY)
                            .actor("System")
                            .description("Initial policy creation and baseline versioning")
                            .timestamp(Instant.now())
                            .build();
                    policyTransactionRepository.save(txn);
                }
            } catch (Exception e) {
                log.warn("Could not initialize sample data (MongoDB may be offline): {}", e.getMessage());
            }
        };
    }

    /** Slug mẫu cho agent seed — link test được: /a/dai-ly-mau (Track C). */
    static final String SEED_AGENT_SLUG = "dai-ly-mau";

    /**
     * Find-or-create agent seed (SaaS Phase 1). Lỗi ở đây không được phá seed
     * user/policy phía sau nên bọc try/catch riêng, chỉ log warn (theo tinh thần
     * failure-isolated của runner này, vốn đã bọc toàn bộ trong try/catch).
     */
    private Agent seedAgent() {
        try {
            Agent existing = agentRepository.findByCode(SEED_AGENT_CODE).orElse(null);
            if (existing != null) {
                // Backfill Track C: DB đã seed từ trước khi có slug thì agent
                // seed chưa có slug → gán slug mẫu (chỉ khi chưa ai giữ slug
                // đó; nếu đã có agent khác giữ thì giữ nguyên + warn, không
                // phá link của agent kia).
                if (existing.getSlug() == null || existing.getSlug().isBlank()) {
                    if (!agentRepository.existsBySlug(SEED_AGENT_SLUG)) {
                        existing.setSlug(SEED_AGENT_SLUG);
                        existing.setUpdatedAt(Instant.now());
                        Agent updated = agentRepository.save(existing);
                        log.info("Backfilled slug {} cho agent seed {}", SEED_AGENT_SLUG, SEED_AGENT_CODE);
                        return updated;
                    }
                    log.warn("Slug {} đã có chủ, không backfill cho agent seed {}", SEED_AGENT_SLUG, SEED_AGENT_CODE);
                }
                return existing;
            }
            Agent agent = Agent.builder()
                    .code(SEED_AGENT_CODE)
                    .slug(SEED_AGENT_SLUG)
                    .displayName("Đại Lý Mẫu")
                    .contactName("Đại Lý Mẫu")
                    .contactEmail(SEED_AGENT_EMAIL)
                    .status(AgentStatus.ACTIVE)
                    .createdBy("seed")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            Agent saved = agentRepository.save(agent);
            log.info("Seeded agent {} ({})", SEED_AGENT_CODE, SEED_AGENT_EMAIL);
            return saved;
        } catch (Exception e) {
            log.warn("Could not seed agent {}: {}", SEED_AGENT_CODE, e.getMessage());
            return null;
        }
    }

    /**
     * Gán agentId của agent seed cho agent@example.com — CHỈ khi user đó chưa
     * có agentId. User đã được ADMIN gán tenant khác thì giữ nguyên (seed chạy
     * lại không được phá quyết định tay của ADMIN).
     */
    private void assignSeedAgentToDemoAgent(Agent seedAgent) {
        if (seedAgent == null || seedAgent.getId() == null) {
            return;
        }
        try {
            userRepository.findByEmail(SEED_AGENT_EMAIL).ifPresent(user -> {
                if (user.getAgentId() == null) {
                    user.setAgentId(seedAgent.getId());
                    userRepository.save(user);
                    log.info("Assigned seed agent {} to {}", SEED_AGENT_CODE, SEED_AGENT_EMAIL);
                }
            });
        } catch (Exception e) {
            log.warn("Could not assign seed agent to {}: {}", SEED_AGENT_EMAIL, e.getMessage());
        }
    }

    private void seedDemoUser(String email, String rawPassword, String fullName, List<String> roles) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        User demo = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .roles(new ArrayList<>(roles))
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(demo);
        log.info("Seeded demo user {} with roles {}", email, roles);
    }

    /**
     * Sửa một lần tài khoản demo kế toán đã seed sai ROLE_ADMIN (trước PR
     * finance-scope): đổi roles thành [ROLE_FINANCE] và bỏ hậu tố "(Admin)"
     * trong tên hiển thị. Idempotent — DB mới (seed đúng) hoặc đã sửa rồi
     * thì không làm gì; không chạm password/enabled/agentId.
     */
    private void repairFinanceDemoUser() {
        try {
            userRepository.findByEmail("finance@example.com").ifPresent(user -> {
                List<String> roles = user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>();
                boolean needsFix = roles.contains("ROLE_ADMIN") || !roles.contains("ROLE_FINANCE");
                String fixedName = user.getFullName() != null
                        ? user.getFullName().replace(" (Admin)", "").trim() : null;
                boolean nameNeedsFix = fixedName != null && !fixedName.equals(user.getFullName());
                if (!needsFix && !nameNeedsFix) {
                    return;
                }
                user.setRoles(List.of("ROLE_FINANCE"));
                if (nameNeedsFix) {
                    user.setFullName(fixedName);
                }
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
                log.info("Repaired demo finance user roles -> [ROLE_FINANCE]");
            });
        } catch (Exception e) {
            log.warn("Could not repair demo finance user: {}", e.getMessage());
        }
    }
}
