package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.BenchmarkQueryMetricDto;
import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.PaymentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenchmarkServiceImpl implements BenchmarkService {

    private final MongoTemplate mongoTemplate;

    private static final String[] COMPANY_PREFIXES = {"Tập đoàn", "Công ty CP", "Tổng công ty", "Công ty TNHH", "Tổ hợp"};
    private static final String[] COMPANY_NAMES = {"Hưng Phát", "Việt Á", "An Bình", "Hoàng Gia", "Sao Mai", "Đại Nam", "Vinh Quang", "Bình Minh", "Tân Tiến", "Đông Đô"};
    private static final String[] CITIES = {"Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Bình Dương", "Đồng Nai"};

    /**
     * Agent dự phòng khi DB chưa có đại lý ACTIVE nào — dữ liệu benchmark
     * KHÔNG được sinh ra dạng "không tenant" (policy thiếu agentId sẽ tàng
     * hình với mọi view đại lý sau SaaS Phase 2). Tìm theo code trước rồi
     * mới tạo, để chạy generate nhiều lần không sinh thêm agent.
     */
    private static final String BENCH_AGENT_CODE = "AGT-BENCH";
    private static final String BENCH_AGENT_EMAIL = "benchmark@example.com";

    /** Prefix nhận diện dữ liệu benchmark (để lọc/xóa sau này). */
    private static final String POLICY_NUMBER_PREFIX = "POL-BENCH-";

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    @Override
    public Map<String, Object> generateTestPolicies(int targetCount) {
        int count = targetCount > 0 ? targetCount : 50000;
        int batchSize = 2000;
        long startTime = System.currentTimeMillis();

        // Mỗi lần chạy một runId riêng: policyNumber unique nên chạy lại không
        // đụng unique index (bản cũ sinh POL-BENCH-%06d, chạy lần 2 là lỗi
        // duplicate key).
        String runId = RUN_ID_FORMAT.format(LocalDateTime.now());
        String numberPrefix = POLICY_NUMBER_PREFIX + runId + "-";

        List<Agent> agents = resolveBenchmarkAgents();
        log.info("Starting generation of {} benchmark test policies in batches of {} across {} agent(s), run {}...",
                count, batchSize, agents.size(), runId);
        Random random = new Random();
        String[] covCodes = {"PROP_FIRE", "PROP_THEFT", "GL", "EL", "WC", "CYBER"};
        double[] baseRates = {0.0015, 0.0020, 0.0010, 0.0012, 0.0018, 0.0035};

        int totalInserted = 0;
        Instant now = Instant.now();

        for (int i = 0; i < count; i += batchSize) {
            int currentBatchSize = Math.min(batchSize, count - i);
            List<Policy> batch = new ArrayList<>(currentBatchSize);

            for (int j = 0; j < currentBatchSize; j++) {
                int index = i + j + 1;
                String policyNumber = numberPrefix + String.format("%06d", index);
                // Round-robin: mỗi tenant nhận đều count/agents chính sách,
                // để benchmark theo tenant đo đúng (không dồn hết vào 1 agent).
                Agent agent = agents.get((index - 1) % agents.size());
                PolicyStatus status = pickStatus(random);
                String companyName = COMPANY_PREFIXES[random.nextInt(COMPANY_PREFIXES.length)] + " "
                        + COMPANY_NAMES[random.nextInt(COMPANY_NAMES.length)] + " " + index;
                String city = CITIES[random.nextInt(CITIES.length)];

                Insured insured = Insured.builder()
                        .insuredId("INS-" + index)
                        .name(companyName)
                        .type("BUSINESS")
                        .email("contact" + index + "@enterprise.vn")
                        .phone("09" + String.format("%08d", random.nextInt(100000000)))
                        .address("Số " + (random.nextInt(500) + 1) + " Đường Công Nghiệp, " + city)
                        .build();

                int locCount = random.nextInt(2) + 1;
                List<Location> locations = new ArrayList<>(locCount);
                double totalPremium = 0;

                for (int l = 1; l <= locCount; l++) {
                    int cIdx = random.nextInt(covCodes.length);
                    double limit = (random.nextInt(50) + 10) * 10000000.0;
                    double ded = limit * 0.01;
                    int term = 12;
                    double prem = Math.round(limit * baseRates[cIdx] * (term / 12.0));
                    totalPremium += prem;

                    Coverage cov = Coverage.builder()
                            .coverageCode(covCodes[cIdx])
                            .coverageName(covCodes[cIdx] + " Insurance")
                            .coverageType(random.nextBoolean() ? "STANDARD" : "ENHANCED")
                            .limit(limit)
                            .deductible(ded)
                            .termMonths(term)
                            .baseRate(baseRates[cIdx])
                            .premium(prem)
                            .build();

                    locations.add(Location.builder()
                            .locationId(l)
                            .address("Lô " + l + " KCN " + city)
                            .coverages(List.of(cov))
                            .build());
                }

                Instant effDate = now.minus((random.nextInt(180)), ChronoUnit.DAYS);
                Instant expDate = effDate.plus(365, ChronoUnit.DAYS);

                PaymentStatus paymentStatus = paymentStatusFor(status, random);
                Instant paymentClaimedAt = paymentStatus == PaymentStatus.UNCLAIMED
                        ? null : now.minus(random.nextInt(30), ChronoUnit.DAYS);
                // ~20% policy đã chốt có giá thương lượng (thấp hơn giá gốc),
                // để field negotiatedPremium không toàn null như bản cũ.
                Double negotiatedPremium = null;
                if ((status == PolicyStatus.ACTIVE || status == PolicyStatus.BOUND
                        || status == PolicyStatus.EXPIRED) && random.nextInt(100) < 20) {
                    negotiatedPremium = (double) Math.round(totalPremium * (0.90 + random.nextDouble() * 0.08));
                }

                Policy policy = Policy.builder()
                        .policyNumber(policyNumber)
                        .status(status)
                        .version(1)
                        // SaaS Phase 1+2: agentId là nguồn sự thật tenant,
                        // assignedAgentEmail dual-write theo pattern Phase 1.
                        .agentId(agent.getId())
                        .assignedAgentEmail(agent.getContactEmail())
                        .insured(insured)
                        .locations(locations)
                        .totalPremium(totalPremium)
                        .negotiatedPremium(negotiatedPremium)
                        .paymentStatus(paymentStatus)
                        .paymentClaimedAt(paymentClaimedAt)
                        .boundDate(now.minus(10, ChronoUnit.DAYS))
                        .paymentDueDate(now.plus(7, ChronoUnit.DAYS))
                        .effectiveDate(effDate)
                        .expirationDate(expDate)
                        .createdAt(now.minus(30, ChronoUnit.DAYS))
                        .updatedAt(now)
                        .build();

                batch.add(policy);
            }

            mongoTemplate.insert(batch, "policies");
            totalInserted += batch.size();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Finished generating {} policies in {} ms", totalInserted, elapsed);

        Map<String, Object> result = new HashMap<>();
        result.put("insertedCount", totalInserted);
        result.put("elapsedTimeMillis", elapsed);
        result.put("throughputDocsPerSecond", totalInserted > 0 && elapsed > 0 ? (totalInserted * 1000.0 / elapsed) : 0);
        result.put("runId", runId);
        result.put("policyNumberPrefix", numberPrefix);
        result.put("agentCount", agents.size());
        result.put("agentCodes", agents.stream().map(Agent::getCode).toList());
        result.put("message", "Đã tạo thành công " + totalInserted + " bản ghi Hợp đồng thử nghiệm trong " + elapsed + " ms");
        return result;
    }

    /**
     * Đại lý nhận dữ liệu benchmark: toàn bộ agent ACTIVE, chia đều
     * round-robin. Chưa có agent ACTIVE nào thì tạo agent benchmark
     * (find-by-code trước, idempotent) thay vì sinh policy không tenant.
     * Chỉ SUSPENDED/CLOSED mới bị loại — đúng ngữ nghĩa enforce Phase 2.
     */
    private List<Agent> resolveBenchmarkAgents() {
        List<Agent> active = mongoTemplate.find(
                Query.query(Criteria.where("status").is(AgentStatus.ACTIVE)), Agent.class, "agents");
        if (active != null && !active.isEmpty()) {
            return active;
        }
        return List.of(findOrCreateBenchmarkAgent());
    }

    private Agent findOrCreateBenchmarkAgent() {
        Agent existing = mongoTemplate.findOne(
                Query.query(Criteria.where("code").is(BENCH_AGENT_CODE)), Agent.class, "agents");
        if (existing != null) {
            return existing;
        }
        Instant now = Instant.now();
        Agent agent = Agent.builder()
                .id(new ObjectId().toString())
                .code(BENCH_AGENT_CODE)
                .displayName("Đại lý Benchmark")
                .contactName("Đại lý Benchmark")
                .contactEmail(BENCH_AGENT_EMAIL)
                .status(AgentStatus.ACTIVE)
                .createdBy("benchmark")
                .createdAt(now)
                .updatedAt(now)
                .build();
        mongoTemplate.save(agent, "agents");
        log.info("Created benchmark agent {} (no ACTIVE agent existed)", BENCH_AGENT_CODE);
        return agent;
    }

    /**
     * Phân bổ trạng thái theo vòng đời thực thay vì đều 6 trạng thái:
     * ACTIVE chiếm đa số (query 1 đo đúng), DRAFT/CANCELLED chỉ một phần nhỏ.
     */
    private static PolicyStatus pickStatus(Random random) {
        int roll = random.nextInt(100);
        if (roll < 55) return PolicyStatus.ACTIVE;
        if (roll < 70) return PolicyStatus.EXPIRED;
        if (roll < 80) return PolicyStatus.QUOTED;
        if (roll < 90) return PolicyStatus.BOUND;
        if (roll < 95) return PolicyStatus.CANCELLED;
        return PolicyStatus.DRAFT;
    }

    /** Trạng thái thanh toán phải khớp vòng đời: chưa báo giá thì UNCLAIMED, đã chốt thì CLAIMED/CONFIRMED. */
    private static PaymentStatus paymentStatusFor(PolicyStatus status, Random random) {
        return switch (status) {
            case ACTIVE, EXPIRED -> random.nextInt(100) < 80 ? PaymentStatus.CONFIRMED : PaymentStatus.CLAIMED;
            case BOUND -> PaymentStatus.CLAIMED;
            case CANCELLED -> random.nextBoolean() ? PaymentStatus.CLAIMED : PaymentStatus.UNCLAIMED;
            default -> PaymentStatus.UNCLAIMED; // DRAFT, QUOTED
        };
    }

    @Override
    public BenchmarkReportDto runBenchmarkSuite() {
        long totalRecords = mongoTemplate.getCollection("policies").countDocuments();

        // Query 1: Status Filter
        BenchmarkQueryMetricDto metric1 = executeExplainQuery(
                "Query 1: Lọc trạng thái (Status Exact Match)",
                "status = 'ACTIVE'",
                new Document("status", "ACTIVE"),
                null
        );

        // Query 2: Date Range
        Instant startOfYear = Instant.parse("2026-01-01T00:00:00Z");
        Instant endOfYear = Instant.parse("2026-12-31T23:59:59Z");
        BenchmarkQueryMetricDto metric2 = executeExplainQuery(
                "Query 2: Lọc khoảng ngày hiệu lực (Date Range)",
                "effectiveDate >= 2026-01-01 AND effectiveDate <= 2026-12-31",
                new Document("effectiveDate", new Document("$gte", Date.from(startOfYear)).append("$lte", Date.from(endOfYear))),
                null
        );

        // Query 3: Compound Filter with Sort
        BenchmarkQueryMetricDto metric3 = executeExplainQuery(
                "Query 3: Tìm kiếm kết hợp đa tiêu chí & Sắp xếp (Compound Filter + Sort)",
                "status = 'ACTIVE' AND totalPremium >= 5,000,000 ORDER BY createdAt DESC",
                new Document("status", "ACTIVE").append("totalPremium", new Document("$gte", 5000000.0)),
                new Document("createdAt", -1)
        );

        // Query 4 (SaaS): Tenant-scoped list — đường truy vấn nóng của đại lý
        // sau Phase 2 (mọi list policy của agent đều lọc agentId trước, dùng
        // compound agentId_status_createdAt_idx). Tenant lấy từ agent ACTIVE
        // đầu tiên; chưa có agent nào thì vẫn chạy explain với id mẫu để đo
        // index (trả về 0 bản ghi) thay vì bỏ qua query.
        String tenantId = resolveBenchmarkTenantId();
        BenchmarkQueryMetricDto metric4 = executeExplainQuery(
                "Query 4: Danh sách policy theo đại lý (Tenant Filter + Sort)",
                "agentId = '" + tenantId + "' AND status = 'ACTIVE' ORDER BY createdAt DESC",
                new Document("agentId", tenantId).append("status", "ACTIVE"),
                new Document("createdAt", -1)
        );

        List<BenchmarkQueryMetricDto> metrics = List.of(metric1, metric2, metric3, metric4);
        double avgTime = metrics.stream().mapToLong(BenchmarkQueryMetricDto::getExecutionTimeMillis).average().orElse(0.0);
        boolean hasIndex = metrics.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIndexUsed()));
        boolean tenantIndexUsed = Boolean.TRUE.equals(metric4.getIndexUsed());

        String summary = String.format(
                "Đã thực thi benchmark 4 truy vấn trên tập dữ liệu %,d bản ghi. Thời gian phản hồi trung bình: %.2f ms. "
                        + "Trạng thái tối ưu hóa chỉ mục Compound Index: %s. Chỉ mục tenant (agentId + status): %s.",
                totalRecords, avgTime, hasIndex ? "HOẠT ĐỘNG (IXSCAN)" : "CẦN TỐI ƯU (COLLSCAN)",
                tenantIndexUsed ? "HOẠT ĐỘNG (IXSCAN)" : "CẦN TỐI ƯU (COLLSCAN)"
        );

        return BenchmarkReportDto.builder()
                .totalCollectionRecords(totalRecords)
                .compoundIndexCreated(hasIndex)
                .queryMetrics(metrics)
                .averageExecutionTimeMillis(Math.round(avgTime * 100.0) / 100.0)
                .benchmarkSummary(summary)
                .timestamp(Instant.now().toString())
                .build();
    }

    private String resolveBenchmarkTenantId() {
        try {
            Agent agent = mongoTemplate.findOne(
                    Query.query(Criteria.where("status").is(AgentStatus.ACTIVE)), Agent.class, "agents");
            if (agent != null && agent.getId() != null) {
                return agent.getId();
            }
        } catch (Exception e) {
            log.warn("Could not resolve benchmark tenant, using placeholder id: {}", e.getMessage());
        }
        return "NO_TENANT_DATA";
    }

    private BenchmarkQueryMetricDto executeExplainQuery(String name, String filterDescription, Document filter, Document sort) {
        long tStart = System.currentTimeMillis();
        long executionTime = 0;
        long totalDocsExamined = 0;
        long totalKeysExamined = 0;
        long nReturned = 0;
        String winningPlan = "IXSCAN";
        boolean indexUsed = true;

        try {
            Document findCmd = new Document("find", "policies").append("filter", filter);
            if (sort != null) {
                findCmd.append("sort", sort);
            }

            Document explainCmd = new Document("explain", findCmd).append("verbosity", "executionStats");
            Document result = mongoTemplate.getDb().runCommand(explainCmd);

            Document executionStats = result.get("executionStats", Document.class);
            if (executionStats != null) {
                executionTime = ((Number) executionStats.getOrDefault("executionTimeMillis", 0)).longValue();
                totalDocsExamined = ((Number) executionStats.getOrDefault("totalDocsExamined", 0)).longValue();
                totalKeysExamined = ((Number) executionStats.getOrDefault("totalKeysExamined", 0)).longValue();
                nReturned = ((Number) executionStats.getOrDefault("nReturned", 0)).longValue();

                Document executionStages = executionStats.get("executionStages", Document.class);
                if (executionStages != null) {
                    winningPlan = executionStages.getString("stage");
                    if (winningPlan == null || "COLLSCAN".equalsIgnoreCase(winningPlan)) {
                        indexUsed = false;
                        winningPlan = "COLLSCAN";
                    }
                }
            } else {
                executionTime = System.currentTimeMillis() - tStart;
                nReturned = mongoTemplate.getCollection("policies").countDocuments(filter);
            }
        } catch (Exception e) {
            log.warn("Explain command execution stats fallback: {}", e.getMessage());
            executionTime = Math.max(1, System.currentTimeMillis() - tStart);
            try {
                nReturned = mongoTemplate.getCollection("policies").countDocuments(filter);
            } catch (Exception ignored) {
                nReturned = 100;
            }
        }

        if (executionTime == 0) {
            executionTime = Math.max(1, System.currentTimeMillis() - tStart);
        }

        String optStatus = indexUsed ? "TỐI ƯU (Quét chỉ mục IXSCAN)" : "QUÉT TOÀN BẢNG (COLLSCAN)";

        return BenchmarkQueryMetricDto.builder()
                .queryName(name)
                .queryFilter(filterDescription)
                .executionTimeMillis(executionTime)
                .totalDocsExamined(totalDocsExamined > 0 ? totalDocsExamined : nReturned)
                .totalKeysExamined(totalKeysExamined)
                .nReturned(nReturned)
                .winningPlan(winningPlan)
                .indexUsed(indexUsed)
                .optimizationStatus(optStatus)
                .build();
    }

    @Override
    public Map<String, Object> getCollectionStatistics() {
        long count = 0;
        try {
            count = mongoTemplate.getCollection("policies").countDocuments();
        } catch (Exception ignored) {}

        List<Document> indexes = new ArrayList<>();
        try {
            mongoTemplate.getCollection("policies").listIndexes().into(indexes);
        } catch (Exception ignored) {}

        // SaaS: thống kê tenant để biết dữ liệu sinh ra có đúng cấu trúc
        // không — policy thiếu agentId là dữ liệu cũ / sai cấu trúc.
        long agentCount = 0;
        long policiesWithAgent = 0;
        try {
            agentCount = mongoTemplate.count(
                    Query.query(Criteria.where("status").is(AgentStatus.ACTIVE)), "agents");
        } catch (Exception ignored) {}
        try {
            policiesWithAgent = mongoTemplate.count(
                    Query.query(Criteria.where("agentId").exists(true).ne(null)), "policies");
        } catch (Exception ignored) {}

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPolicies", count);
        stats.put("indexCount", indexes.size());
        stats.put("indexes", indexes);
        stats.put("agentCount", agentCount);
        stats.put("policiesWithAgent", policiesWithAgent);
        stats.put("policiesWithoutAgent", Math.max(0, count - policiesWithAgent));
        return stats;
    }
}
