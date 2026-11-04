package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.CommissionRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Migration dữ liệu cũ sang tenant — SaaS Phase 1, PLAN-SAAS §5.
 *
 * <p>Thứ tự bắt buộc: Agent → User → Policy → Commission. Mọi bước đều
 * idempotent: chạy lại lần hai cho 0 thay đổi (record đã có agentId không bao
 * giờ bị ghi đè — bảo vệ việc ADMIN gán tay sau lần chạy đầu).</p>
 *
 * <p>CHẾ ĐỘ DRY-RUN: {@link #dryRun()} chỉ đọc, không ghi document nào, không
 * sinh mã (không chạm counter). Đây là chế độ DUY NHẤT được phép chạy trong
 * Phase 1; {@link #apply()} tồn tại để test chứng minh tính idempotent trên
 * mock và để Phase 2 gọi sau khi có duyệt riêng — Phase 1 KHÔNG gắn migration
 * vào bất kỳ endpoint HTTP nào, nên không thể vô tình chạy trên dữ liệu thật
 * qua API.</p>
 *
 * <p>Nguyên tắc sắt: {@code assignedAgentEmail} không bao giờ bị xóa hay sửa;
 * {@code agentId} chỉ được THÊM. Policy/user chưa xác định được tenant thì để
 * null + vào danh sách cho ADMIN gán tay — cấm gán bừa về ADMIN hay tenant
 * mặc định (§5.6).</p>
 *
 * <p>Độ lệch duy nhất so với chữ §5.3 (ghi rõ để Track 2/Phase 2 khỏi tưởng là
 * bug): §5.3 nói customer đa tenant "để null" — với baseline legacy (agentId
 * toàn null) thì lần chạy đầu đúng là để null; nhưng nếu user đó ĐÃ có agentId
 * (ADMIN gán tay sau lần chạy đầu) thì chạy lại KHÔNG xóa về null, mà đếm vào
 * skippedAlreadyAssigned. Lý do: migration chạy lại không được phá quyết định
 * tay của ADMIN.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMigrationService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final CommissionRepository commissionRepository;
    private final AgentCodeGenerator codeGenerator;

    /** Chỉ báo cáo sẽ đổi gì, không ghi gì, không sinh mã. */
    public TenantMigrationReport dryRun() {
        return execute(true);
    }

    /**
     * Chạy migration thật. Phase 1: CHỈ gọi từ unit test (mock) để chứng minh
     * idempotent; không gắn endpoint, không chạy trên dữ liệu thật.
     */
    public TenantMigrationReport apply() {
        return execute(false);
    }

    private TenantMigrationReport execute(boolean dryRun) {
        List<Policy> policies = policyRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Commission> commissions = commissionRepository.findAll();
        List<Agent> existingAgents = agentRepository.findAll();

        TenantMigrationReport.TenantMigrationReportBuilder report = TenantMigrationReport.builder()
                .dryRun(dryRun);

        // ---- Bước 1: map email chuẩn hóa -> Agent (đã có + sẽ tạo) ----
        Map<String, Agent> agentByEmail = new HashMap<>();
        existingAgents.stream()
                .sorted(Comparator.comparing(Agent::getCode, Comparator.nullsLast(String::compareTo)))
                .forEach(agent -> {
                    String email = normalizeEmail(agent.getContactEmail());
                    if (email != null) {
                        agentByEmail.putIfAbsent(email, agent);
                    }
                });

        Map<String, User> agentUserByEmail = new HashMap<>();
        for (User user : users) {
            if (hasRole(user, Role.ROLE_AGENT)) {
                String email = normalizeEmail(user.getEmail());
                if (email != null) {
                    agentUserByEmail.putIfAbsent(email, user);
                }
            }
        }

        Set<String> plannedEmails = new TreeSet<>();
        for (Policy policy : policies) {
            String email = normalizeEmail(policy.getAssignedAgentEmail());
            if (email != null) {
                plannedEmails.add(email);
            }
        }
        plannedEmails.addAll(agentUserByEmail.keySet());

        List<TenantMigrationReport.PlannedAgent> agentsToCreate = new ArrayList<>();
        for (String email : plannedEmails) {
            if (agentByEmail.containsKey(email)) {
                continue;
            }
            String displayName = displayNameFor(email, agentUserByEmail.get(email));
            if (dryRun) {
                agentsToCreate.add(new TenantMigrationReport.PlannedAgent(email, displayName, null));
            } else {
                Agent created = agentRepository.save(Agent.builder()
                        .code(codeGenerator.nextCode())
                        .displayName(displayName)
                        .contactEmail(email)
                        .status(AgentStatus.ACTIVE)
                        .createdBy("migration")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
                agentByEmail.put(email, created);
                agentsToCreate.add(new TenantMigrationReport.PlannedAgent(
                        email, displayName, created.getCode()));
            }
        }
        report.agentsToCreate(agentsToCreate);

        // email -> agentId (kể cả agent sẽ tạo ở dry-run: id chưa có nên dùng
        // planned marker — chỉ dùng để tính toán bước sau, không ghi).
        Map<String, String> agentIdByEmail = new HashMap<>();
        for (Map.Entry<String, Agent> entry : agentByEmail.entrySet()) {
            if (entry.getValue().getId() != null) {
                agentIdByEmail.put(entry.getKey(), entry.getValue().getId());
            }
        }
        // Ở dry-run, agent sẽ tạo chưa có id: user/policy "sẽ gán" vẫn đếm được
        // nhờ plannedEmails — bước dưới dùng resolvedAgentId() fallback về
        // planned marker thay vì null để khỏi rơi nhầm vào unassigned/orphan.
        // Marker này không bao giờ được ghi (dry-run không save).
        for (String email : plannedEmails) {
            agentIdByEmail.putIfAbsent(email, "planned:" + email);
        }

        // Tenant suy ra cho từng insured email (từ policy, sau resolve):
        // insured.email -> tập tenant. Dùng cho nhánh CUSTOMER ở bước 2.
        Map<String, Set<String>> tenantsByInsuredEmail = new HashMap<>();
        for (Policy policy : policies) {
            String tenantId = resolvedPolicyAgentId(policy, agentIdByEmail);
            String insuredEmail = policy.getInsured() != null
                    ? normalizeEmail(policy.getInsured().getEmail()) : null;
            if (tenantId != null && insuredEmail != null) {
                tenantsByInsuredEmail.computeIfAbsent(insuredEmail, k -> new HashSet<>()).add(tenantId);
            }
        }

        // ---- Bước 2: backfill User.agentId ----
        int usersToUpdate = 0;
        int usersSkipped = 0;
        List<String> multiTenantCustomers = new ArrayList<>();
        List<String> unmappedAgentUsers = new ArrayList<>();
        for (User user : users) {
            if (hasRole(user, Role.ROLE_ADMIN) || hasRole(user, Role.ROLE_UNDERWRITER)) {
                continue; // ADMIN/UW không thuộc tenant nào, không gán.
            }
            String decision = null;
            if (hasRole(user, Role.ROLE_AGENT)) {
                // Đa role AGENT+CUSTOMER: gán theo nhánh AGENT (tenant làm việc).
                String email = normalizeEmail(user.getEmail());
                if (email == null) {
                    unmappedAgentUsers.add(user.getEmail());
                    continue;
                }
                decision = plannedAgentId(email, agentIdByEmail, dryRun);
            } else if (hasRole(user, Role.ROLE_CUSTOMER) || hasRole(user, Role.ROLE_USER)) {
                String email = normalizeEmail(user.getEmail());
                Set<String> tenants = email == null ? Set.of()
                        : tenantsByInsuredEmail.getOrDefault(email, Set.of());
                if (tenants.size() == 1) {
                    decision = tenants.iterator().next();
                } else if (tenants.size() > 1) {
                    multiTenantCustomers.add(email);
                    continue; // để null, ADMIN quyết — cấm đoán tenant "chính".
                }
                // 0 tenant -> để null (khách tự mua / chưa có policy).
            } else {
                continue; // Role lạ, không có nhánh gán — bỏ qua, không đoán.
            }
            if (decision == null) {
                continue;
            }
            if (decision.equals(user.getAgentId())) {
                usersSkipped++;
            } else if (user.getAgentId() != null) {
                // Đã có agentId khác (ADMIN gán tay) — không ghi đè khi chạy lại.
                usersSkipped++;
            } else {
                usersToUpdate++;
                if (!dryRun) {
                    user.setAgentId(decision);
                    userRepository.save(user);
                }
            }
        }
        report.usersToUpdate(usersToUpdate);
        report.usersSkippedAlreadyAssigned(usersSkipped);
        report.multiTenantCustomerEmails(multiTenantCustomers);
        report.unmappedAgentUserEmails(unmappedAgentUsers);

        // ---- Bước 3a: backfill Policy.agentId ----
        int policiesToUpdate = 0;
        int policiesSkipped = 0;
        List<String> unassigned = new ArrayList<>();
        for (Policy policy : policies) {
            if (policy.getAgentId() != null) {
                policiesSkipped++;
                continue;
            }
            String resolved = resolvedPolicyAgentId(policy, agentIdByEmail);
            if (resolved == null) {
                unassigned.add(policy.getPolicyNumber());
                continue;
            }
            policiesToUpdate++;
            if (!dryRun) {
                policy.setAgentId(resolved);
                policyRepository.save(policy);
            }
        }
        report.policiesToUpdate(policiesToUpdate);
        report.policiesSkippedAlreadyAssigned(policiesSkipped);
        report.unassignedPolicyNumbers(unassigned);

        // ---- Bước 3b: backfill Commission.agentId từ Policy ----
        Map<String, Policy> policyByNumber = new HashMap<>();
        for (Policy policy : policies) {
            if (policy.getPolicyNumber() != null) {
                policyByNumber.put(policy.getPolicyNumber(), policy);
            }
        }
        int commissionsToUpdate = 0;
        int commissionsSkipped = 0;
        List<String> orphans = new ArrayList<>();
        for (Commission commission : commissions) {
            if (commission.getAgentId() != null) {
                commissionsSkipped++;
                continue;
            }
            Policy policy = policyByNumber.get(commission.getPolicyNumber());
            String resolved = policy == null ? null : resolvedPolicyAgentId(policy, agentIdByEmail);
            if (resolved == null) {
                orphans.add(commission.getPolicyNumber());
                continue;
            }
            commissionsToUpdate++;
            if (!dryRun) {
                commission.setAgentId(resolved);
                commissionRepository.save(commission);
            }
        }
        report.commissionsToUpdate(commissionsToUpdate);
        report.commissionsSkippedAlreadyAssigned(commissionsSkipped);
        report.orphanCommissionPolicyNumbers(orphans);

        TenantMigrationReport result = report.build();
        log.info("Tenant migration (dryRun={}): agentsToCreate={}, usersToUpdate={}, "
                        + "policiesToUpdate={}, commissionsToUpdate={}, unassignedPolicies={}, "
                        + "orphanCommissions={}, multiTenantCustomers={}",
                dryRun, agentsToCreate.size(), usersToUpdate, policiesToUpdate,
                commissionsToUpdate, unassigned.size(), orphans.size(), multiTenantCustomers.size());
        return result;
    }

    /**
     * AgentId suy ra cho một policy: agentId hiện có thắng; nếu chưa có thì map
     * từ assignedAgentEmail chuẩn hóa. Ở dry-run, agent sẽ tạo cho marker
     * "planned:email" (chỉ để đếm/đối soát, không ghi).
     */
    private static String resolvedPolicyAgentId(Policy policy, Map<String, String> agentIdByEmail) {
        if (policy.getAgentId() != null) {
            return policy.getAgentId();
        }
        String email = normalizeEmail(policy.getAssignedAgentEmail());
        return email == null ? null : agentIdByEmail.get(email);
    }

    /**
     * AgentId của một email agent: ở apply là id thật; ở dry-run, nếu agent đó
     * sẽ tạo mới (chưa có id) thì là marker "planned:email" — marker chỉ dùng
     * để đếm/đối soát trong báo cáo dry-run, không bao giờ được ghi (dry-run
     * không save document nào).
     */
    private static String plannedAgentId(String email, Map<String, String> agentIdByEmail, boolean dryRun) {
        return agentIdByEmail.get(email);
    }

    /** Chuẩn hóa email theo quy ước codebase: trim + lowercase; blank → null. */
    static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * Tên hiển thị cho Agent sinh từ dữ liệu cũ: fullName của user agent tương
     * ứng nếu có, nếu không thì phần trước @ + ghi chú "(tên tạm — ADMIN sửa sau)".
     */
    static String displayNameFor(String normalizedEmail, User agentUser) {
        if (agentUser != null && agentUser.getFullName() != null && !agentUser.getFullName().isBlank()) {
            return agentUser.getFullName().trim();
        }
        int at = normalizedEmail.indexOf('@');
        String localPart = at > 0 ? normalizedEmail.substring(0, at) : normalizedEmail;
        return localPart + " (tên tạm — ADMIN sửa sau)";
    }

    private static boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role.name());
    }
}
