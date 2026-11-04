package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1 — migration email→Agent (PLAN §5), chạy trên mock (dữ liệu test).
 *
 * <p>Dữ liệu test: 2 agent email (agent@example.com có user fullName, other@
 * chỉ có policy), 1 customer đa tenant (shared@), 1 customer 1 tenant (only-a@),
 * 1 customer 0 tenant (solo@), 1 policy unassigned. SaaS Phase 2 Track A đã
 * bỏ bước migration hoa hồng (collection cũ đóng băng, migration không đụng
 * tới). TUYỆT ĐỐI không chạy migration trên dữ liệu thật ở Phase 1.</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantMigrationServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private AgentCodeGenerator codeGenerator;

    @InjectMocks
    private TenantMigrationService migrationService;

    private Policy policy(String number, String assignedEmail, String insuredEmail) {
        return Policy.builder()
                .policyNumber(number)
                .assignedAgentEmail(assignedEmail)
                .agentId(null)
                .insured(Insured.builder().email(insuredEmail).build())
                .build();
    }

    private User user(String email, List<String> roles, String fullName) {
        return User.builder().email(email).roles(roles).fullName(fullName).agentId(null).build();
    }

    /** Trạng thái legacy (chưa có agentId ở đâu). */
    private void stubLegacyData() {
        when(policyRepository.findAll()).thenReturn(List.of(
                policy("POL-M-1", "Agent@Example.com ", "shared@example.com"),
                policy("POL-M-2", "agent@example.com", "only-a@example.com"),
                policy("POL-M-3", "other@example.com", "shared@example.com"),
                policy("POL-M-4", null, "solo@example.com"),
                policy("POL-M-5", "agent@example.com", "only-a@example.com")));
        when(userRepository.findAll()).thenReturn(List.of(
                user("AGENT@example.com", List.of("ROLE_AGENT"), "Đại Lý A"),
                user("other@example.com", List.of("ROLE_AGENT"), null),
                user("shared@example.com", List.of("ROLE_CUSTOMER"), null),
                user("only-a@example.com", List.of("ROLE_CUSTOMER"), null),
                user("solo@example.com", List.of("ROLE_CUSTOMER"), null),
                user("admin@example.com", List.of("ROLE_ADMIN"), null),
                user("   ", List.of("ROLE_AGENT"), null)));
        when(agentRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void dryRun_reportsEverything_writesNothing() {
        stubLegacyData();

        TenantMigrationReport report = migrationService.dryRun();

        assertTrue(report.isDryRun());
        // Agent: email chuẩn hóa (hoa/thường + khoảng trắng gộp thành 1),
        // sort theo email; tên từ fullName user, fallback local-part + ghi chú.
        assertEquals(List.of("agent@example.com", "other@example.com"),
                report.getAgentsToCreate().stream()
                        .map(TenantMigrationReport.PlannedAgent::email).collect(Collectors.toList()));
        assertEquals("Đại Lý A", report.getAgentsToCreate().get(0).displayName());
        assertEquals("other (tên tạm — ADMIN sửa sau)", report.getAgentsToCreate().get(1).displayName());
        assertTrue(report.getAgentsToCreate().stream()
                .allMatch(a -> a.code() == null), "dry-run không sinh mã");

        // User: 2 agent + 1 customer 1-tenant. Shared (đa tenant) không gán,
        // solo (0 tenant) không gán, admin không gán.
        assertEquals(3, report.getUsersToUpdate());
        assertEquals(List.of("shared@example.com"), report.getMultiTenantCustomerEmails());
        assertEquals(1, report.getUnmappedAgentUserEmails().size());

        // Policy: 4 có agent, 1 unassigned (không gán bừa).
        assertEquals(4, report.getPoliciesToUpdate());
        assertEquals(List.of("POL-M-4"), report.getUnassignedPolicyNumbers());

        // DRY-RUN: không ghi gì, không chạm counter.
        verify(agentRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(policyRepository, never()).save(any());
        verify(codeGenerator, never()).nextCode();
    }

    @Test
    void apply_writesExpectedMappings() {
        stubLegacyData();
        when(codeGenerator.nextCode()).thenReturn("AGT-0001", "AGT-0002");
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId("agent@example.com".equals(a.getContactEmail()) ? "tenant-agent" : "tenant-other");
            return a;
        });
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        TenantMigrationReport report = migrationService.apply();

        assertFalse(report.isDryRun());
        assertEquals(List.of("AGT-0001", "AGT-0002"),
                report.getAgentsToCreate().stream()
                        .map(TenantMigrationReport.PlannedAgent::code).collect(Collectors.toList()));
        assertEquals(3, report.getUsersToUpdate());
        assertEquals(4, report.getPoliciesToUpdate());
        verify(agentRepository, times(2)).save(any(Agent.class));
        verify(userRepository, times(3)).save(any(User.class));
        verify(policyRepository, times(4)).save(any(Policy.class));

        // User agent gán đúng tenant, customer đa tenant không bị gán:
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(userCaptor.capture());
        assertTrue(userCaptor.getAllValues().stream()
                .anyMatch(u -> "AGENT@example.com".equals(u.getEmail()) && "tenant-agent".equals(u.getAgentId())));
        assertTrue(userCaptor.getAllValues().stream()
                .noneMatch(u -> "shared@example.com".equals(u.getEmail())));
    }

    @Test
    void apply_secondRun_isIdempotent_zeroChanges() {
        // Trạng thái SAU lần apply đầu: agent đã có, user/policy đã gán.
        Agent agentA = Agent.builder().id("tenant-agent").code("AGT-0001")
                .contactEmail("agent@example.com").status(AgentStatus.ACTIVE).build();
        Agent agentO = Agent.builder().id("tenant-other").code("AGT-0002")
                .contactEmail("other@example.com").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findAll()).thenReturn(List.of(agentA, agentO));

        Policy p1 = policy("POL-M-1", "agent@example.com", "shared@example.com");
        p1.setAgentId("tenant-agent");
        Policy p2 = policy("POL-M-2", "agent@example.com", "only-a@example.com");
        p2.setAgentId("tenant-agent");
        Policy p3 = policy("POL-M-3", "other@example.com", "shared@example.com");
        p3.setAgentId("tenant-other");
        Policy p4 = policy("POL-M-4", null, "solo@example.com");
        when(policyRepository.findAll()).thenReturn(List.of(p1, p2, p3, p4));

        User agentUser = user("agent@example.com", List.of("ROLE_AGENT"), "Đại Lý A");
        agentUser.setAgentId("tenant-agent");
        User custShared = user("shared@example.com", List.of("ROLE_CUSTOMER"), null);
        when(userRepository.findAll()).thenReturn(List.of(agentUser, custShared));

        TenantMigrationReport report = migrationService.apply();

        assertTrue(report.getAgentsToCreate().isEmpty());
        assertEquals(0, report.getUsersToUpdate());
        assertEquals(0, report.getPoliciesToUpdate());
        // Vẫn còn: 1 policy unassigned — cần ADMIN gán tay, migration chạy
        // lại không tự "dọn" nó đi.
        assertEquals(List.of("POL-M-4"), report.getUnassignedPolicyNumbers());
        verify(agentRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(policyRepository, never()).save(any());
        verify(codeGenerator, never()).nextCode();
    }

    @Test
    void pureMapping_normalizeAndDisplayName() {
        assertEquals("agent@example.com", TenantMigrationService.normalizeEmail("  Agent@Example.COM "));
        assertNull(TenantMigrationService.normalizeEmail("   "));
        assertNull(TenantMigrationService.normalizeEmail(null));
        assertEquals("Đại Lý A", TenantMigrationService.displayNameFor("agent@example.com",
                user("agent@example.com", List.of("ROLE_AGENT"), "Đại Lý A")));
        assertEquals("other (tên tạm — ADMIN sửa sau)", TenantMigrationService.displayNameFor(
                "other@example.com", user("other@example.com", List.of("ROLE_AGENT"), "  ")));
        assertEquals("other (tên tạm — ADMIN sửa sau)", TenantMigrationService.displayNameFor(
                "other@example.com", null));
    }
}
