package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1 dual-write ở PolicyServiceImpl.createPolicy:
 * ghi cả agentId (mới) và assignedAgentEmail (cũ).
 *
 * <p>Chú ý: permissionService ở đây là mock nên currentTenantId() phải stub
 * tường minh; production bean thật đọc TenantContext do filter resolve (đã
 * khóa ở PermissionServiceTenantTest + JwtAuthenticationFilterTenantTest).</p>
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplDualWriteTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private PolicyServiceImpl policyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(policyService, "permissionService", permissionService);
        ReflectionTestUtils.setField(policyService, "agentRepository", agentRepository);
        // Lenient: fixture chung — từng test dùng một tập con (vd test throw
        // trước save thì stub save không được chạm tới, test admin/customer
        // không chạm tới findById("tenant-1")).
        lenient().when(policyRepository.existsByPolicyNumber(anyString())).thenReturn(false);
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(agentRepository.findById("tenant-1"))
                .thenReturn(Optional.of(Agent.builder().id("tenant-1").displayName("Đại Lý Mẫu").build()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void asAgent(String tenantId) {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("agent@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(false);
        when(permissionService.hasAgentRole()).thenReturn(true);
        when(permissionService.currentTenantId()).thenReturn(tenantId);
    }

    private Policy savedPolicy() {
        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(policyRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void agentCreate_writesBothAgentIdAndAssignedEmail() {
        asAgent("tenant-1");
        PolicyResponse response = policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-DW-1").build());

        Policy saved = savedPolicy();
        assertEquals("tenant-1", saved.getAgentId());
        assertEquals("agent@example.com", saved.getAssignedAgentEmail());
        assertEquals("tenant-1", response.getAgentId());
        assertEquals("Đại Lý Mẫu", response.getAgentName());
        assertEquals("agent@example.com", response.getAssignedAgentEmail());
    }

    @Test
    void agentCreate_ignoresBodyAgentId_contextWins() {
        asAgent("tenant-1");
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-DW-2").agentId("tenant-evil").build());
        assertEquals("tenant-1", savedPolicy().getAgentId());
    }

    @Test
    void agentCreate_withoutTenant_writesNullAgentId_notFailClosed() {
        // Phase 1: agent chưa gán tenant KHÔNG bị fail-closed — policy vẫn tạo,
        // agentId null, assignedAgentEmail vẫn ghi như cũ.
        asAgent(null);
        policyService.createPolicy(CreatePolicyRequest.builder().policyNumber("POL-DW-3").build());
        Policy saved = savedPolicy();
        assertNull(saved.getAgentId());
        assertEquals("agent@example.com", saved.getAssignedAgentEmail());
    }

    @Test
    void adminCreate_usesChosenAgentId() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(agentRepository.existsById("tenant-9")).thenReturn(true);
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-DW-4").agentId("tenant-9").build());
        Policy saved = savedPolicy();
        assertEquals("tenant-9", saved.getAgentId());
        assertNull(saved.getAssignedAgentEmail(), "ADMIN tạo hộ không ép email agent");
    }

    @Test
    void adminCreate_withoutAgentId_leavesNull() {
        when(permissionService.isAdmin()).thenReturn(true);
        policyService.createPolicy(CreatePolicyRequest.builder().policyNumber("POL-DW-5").build());
        assertNull(savedPolicy().getAgentId());
        verify(agentRepository, never()).existsById(anyString());
    }

    @Test
    void adminCreate_unknownAgentId_rejectedBeforeSave() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(agentRepository.existsById("tenant-ghost")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-DW-6").agentId("tenant-ghost").build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void customerCreate_agentIdAlwaysNull() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("cust@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(true);
        // Nhánh CUSTOMER không hề đọc currentTenantId() (code chỉ đọc tenant
        // ở nhánh AGENT) nên không stub nó — agentId null là do nhánh, không
        // phải do tenant trống.
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-DW-7").agentId("tenant-1").build());
        assertNull(savedPolicy().getAgentId(), "CUSTOMER tự tạo: agentId luôn null, kể cả khi body/context có tenant");
    }

    @Test
    void response_withoutAgent_agentNameNullSafe() {
        when(permissionService.isAdmin()).thenReturn(true);
        PolicyResponse response = policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-DW-8").build());
        assertNull(response.getAgentId());
        assertNull(response.getAgentName());
        verify(agentRepository, never()).findById(anyString());
    }
}
