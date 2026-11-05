package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đợt 1C: chỉ overload createPolicy(CreatePolicyRequest) bị chặn với
 * UNDERWRITER. ADMIN (kể cả mang đồng thời UW) vẫn qua;
 * CUSTOMER / AGENT giữ nguyên ép scope như cũ (test customer ở đây).
 *
 * <p>SaaS Phase 2 Track C (sửa có chủ đích, không phải sửa để chiều code):
 * ADMIN tạo hộ nay BẮT BUỘC có agentId hợp lệ (trước đây được để trống →
 * null) và CUSTOMER tự mua BẮT BUỘC có agentSlug hợp lệ (trước đây agentId
 * luôn null) — nên hai test dưới đây phải kèm agentId/agentSlug + stub
 * AgentRepository, nếu không sẽ 400 theo đúng quy tắc mới.</p>
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplCreatePolicyRoleTest {

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
    }

    private CreatePolicyRequest request() {
        return CreatePolicyRequest.builder().policyNumber("POL-GATE-1").build();
    }

    @Test
    void createPolicy_underwriter_throwsForbidden_beforeDuplicateCheck() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> policyService.createPolicy(request()));
        assertEquals("Vai trò UNDERWRITER không được tạo hợp đồng.", ex.getMessage());
        // Gate đặt trước duplicate-check: không lộ policyNumber đã tồn tại.
        verify(policyRepository, never()).existsByPolicyNumber(anyString());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_adminAlsoUnderwriter_passesGate() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(agentRepository.existsById("tenant-9")).thenReturn(true);
        when(policyRepository.existsByPolicyNumber("POL-GATE-1")).thenReturn(false);
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        // Track C: ADMIN tạo hộ bắt buộc có agentId — request không agentId
        // nay 400 nên test này phải kèm agentId hợp lệ.
        assertNotNull(policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-GATE-1").agentId("tenant-9").build()));
        verify(policyRepository).save(any(Policy.class));
    }

    @Test
    void createPolicy_customer_passesGateAndKeepsScopeForcing() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("cust@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(true);
        when(agentRepository.findBySlug("dai-ly-mau")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").displayName("Đại Lý Mẫu")
                        .slug("dai-ly-mau").status(AgentStatus.ACTIVE).build()));
        when(policyRepository.existsByPolicyNumber("POL-GATE-1")).thenReturn(false);
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        // Track C: CUSTOMER tự mua bắt buộc có agentSlug — request không slug
        // nay 400 nên test này phải kèm agentSlug hợp lệ; body agentId vẫn bị
        // bỏ qua, agentId gắn từ Agent đã resolve.
        assertNotNull(policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-GATE-1").agentSlug("dai-ly-mau").build()));
        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(policyRepository).save(captor.capture());
        assertEquals("agent-1", captor.getValue().getAgentId());
    }
}
