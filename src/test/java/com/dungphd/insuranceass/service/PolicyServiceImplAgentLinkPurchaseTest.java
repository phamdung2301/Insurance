package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 2 Track C: ma trận mua mới bắt buộc có đại lý ở
 * PolicyServiceImpl.createPolicy.
 *
 * <p>Setup mock permissionService nên nhánh AGENT "chưa gán tenant → 403" ở
 * đây được giả lập bằng doThrow trên checkTenantWriteForStaff (mock void
 * không tự enforce như bean thật); enforcement thật của guard đó đã khóa ở
 * TenantScopeCutoverTest với PermissionService thật.</p>
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplAgentLinkPurchaseTest {

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
        lenient().when(policyRepository.existsByPolicyNumber(anyString())).thenReturn(false);
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));
        Agent active = Agent.builder().id("agent-1").displayName("Đại Lý Mẫu")
                .slug("dai-ly-mau").status(AgentStatus.ACTIVE).build();
        lenient().when(agentRepository.findBySlug("dai-ly-mau")).thenReturn(Optional.of(active));
        lenient().when(agentRepository.findById("agent-1")).thenReturn(Optional.of(active));
        lenient().when(agentRepository.findBySlug("dai-ly-treo")).thenReturn(Optional.of(
                Agent.builder().id("agent-2").displayName("Đại Lý Treo")
                        .slug("dai-ly-treo").status(AgentStatus.SUSPENDED).build()));
        lenient().when(agentRepository.findBySlug("dai-ly-dong")).thenReturn(Optional.of(
                Agent.builder().id("agent-3").displayName("Đại Lý Đóng")
                        .slug("dai-ly-dong").status(AgentStatus.CLOSED).build()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void asCustomer() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("cust@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(true);
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
    void customer_withoutAgentSlug_rejected400_noSave() {
        asCustomer();
        assertThrows(BadRequestException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-LINK-1").termsAccepted(true).build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void customer_validSlug_setsResolvedAgentId_ignoresBodyAgentId() {
        asCustomer();
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-LINK-2").agentId("tenant-evil").agentSlug("DAI-LY-MAU").termsAccepted(true).build());
        // Slug chuẩn hóa về chữ thường trước khi tra (input hoa vẫn khớp).
        assertEquals("agent-1", savedPolicy().getAgentId());
        verify(agentRepository).findBySlug("dai-ly-mau");
    }

    @Test
    void customer_unknownSlug_throws404_noSave() {
        asCustomer();
        when(agentRepository.findBySlug("khong-co")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-LINK-3").agentSlug("khong-co").termsAccepted(true).build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void customer_suspendedSlug_throws410_distinctFrom404() {
        asCustomer();
        AgentLinkInactiveException ex = assertThrows(AgentLinkInactiveException.class,
                () -> policyService.createPolicy(CreatePolicyRequest.builder()
                        .policyNumber("POL-LINK-4").agentSlug("dai-ly-treo").termsAccepted(true).build()));
        assertEquals(AgentStatus.SUSPENDED, ex.getStatus());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void customer_closedSlug_throws410() {
        asCustomer();
        AgentLinkInactiveException ex = assertThrows(AgentLinkInactiveException.class,
                () -> policyService.createPolicy(CreatePolicyRequest.builder()
                        .policyNumber("POL-LINK-5").agentSlug("dai-ly-dong").termsAccepted(true).build()));
        assertEquals(AgentStatus.CLOSED, ex.getStatus());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void agent_contextWins_agentSlugIgnored() {
        asAgent("tenant-ctx");
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-LINK-6").agentId("tenant-evil").agentSlug("dai-ly-mau").termsAccepted(true).build());
        assertEquals("tenant-ctx", savedPolicy().getAgentId());
        // agentSlug gửi kèm bị bỏ qua hoàn toàn — không tra slug.
        verify(agentRepository, never()).findBySlug(anyString());
    }

    @Test
    void agent_unassignedTenant_failClosed403() {
        // Không dùng helper asAgent() ở đây: guard throw trước khi code đọc
        // currentTenantId() nên stub currentTenantId trong helper sẽ thành
        // unnecessary stubbing (Mockito strict). Stub tay chỉ những gì luồng
        // này chạm tới (gate UW + ép insured/agent email).
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.currentPrincipal()).thenReturn("agent@example.com");
        when(permissionService.hasCustomerRole()).thenReturn(false);
        when(permissionService.hasAgentRole()).thenReturn(true);
        doThrow(new ForbiddenException("Agent chưa được gán tenant."))
                .when(permissionService).checkTenantWriteForStaff();
        assertThrows(ForbiddenException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-LINK-7").termsAccepted(true).build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void admin_missingAgentId_rejected400() {
        when(permissionService.isAdmin()).thenReturn(true);
        assertThrows(BadRequestException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-LINK-8").termsAccepted(true).build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void admin_unknownAgentId_rejected400() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(agentRepository.existsById("tenant-ghost")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> policyService.createPolicy(
                CreatePolicyRequest.builder().policyNumber("POL-LINK-9").agentId("tenant-ghost").termsAccepted(true).build()));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void admin_validAgentId_used_agentSlugIgnored() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(agentRepository.existsById("tenant-9")).thenReturn(true);
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-LINK-10").agentId("tenant-9").agentSlug("dai-ly-mau").termsAccepted(true).build());
        assertEquals("tenant-9", savedPolicy().getAgentId());
        verify(agentRepository, never()).findBySlug(anyString());
    }

    @Test
    void admin_validSlug_resolvedWhenNoAgentId() {
        // Bug 2026-11-05: ADMIN đi qua wizard/storefront chỉ gửi agentSlug
        // (agentRef) — nhánh ADMIN cũ bỏ qua slug và đòi agentId → 400 dù
        // đại lý đã chọn. Thiếu agentId thì resolve slug như CUSTOMER.
        when(permissionService.isAdmin()).thenReturn(true);
        policyService.createPolicy(CreatePolicyRequest.builder()
                .policyNumber("POL-LINK-11").agentSlug("dai-ly-mau").termsAccepted(true).build());
        assertEquals("agent-1", savedPolicy().getAgentId());
        verify(agentRepository).findBySlug("dai-ly-mau");
        // Id resolve từ slug đã được findBySlug validate nên không cần
        // existsById thêm một lần nữa.
        verify(agentRepository, never()).existsById(anyString());
    }

    @Test
    void admin_suspendedSlug_throws410() {
        // ADMIN qua slug cũng chịu cùng luật ACTIVE như CUSTOMER.
        when(permissionService.isAdmin()).thenReturn(true);
        AgentLinkInactiveException ex = assertThrows(AgentLinkInactiveException.class,
                () -> policyService.createPolicy(CreatePolicyRequest.builder()
                        .policyNumber("POL-LINK-12").agentSlug("dai-ly-treo").termsAccepted(true).build()));
        assertEquals(AgentStatus.SUSPENDED, ex.getStatus());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    // ---- Điều khoản & chính sách: bắt buộc đồng ý trước khi tạo hợp đồng ----

    @Test
    void createPolicy_withoutTermsConsent_throws400() {
        // Consent check chạy TRƯỚC mọi check role nên không cần stub permission.
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyNumber("POL-TERMS-1")
                .agentSlug("dai-ly-mau")
                // không set termsAccepted -> 400
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> policyService.createPolicy(request));
        assertTrue(ex.getMessage().contains("đồng ý Điều khoản"));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_termsAcceptedFalse_throws400() {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyNumber("POL-TERMS-2")
                .agentSlug("dai-ly-mau")
                .termsAccepted(false)
                .build();

        assertThrows(BadRequestException.class, () -> policyService.createPolicy(request));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_termsAccepted_recordsAuditTrail() {
        asCustomer();
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyNumber("POL-TERMS-3")
                .agentSlug("dai-ly-mau")
                .termsAccepted(true)
                .termsVersion("V2026-11")
                .build();

        policyService.createPolicy(request);

        Policy saved = savedPolicy();
        assertNotNull(saved.getTermsAcceptedAt());
        assertEquals("V2026-11", saved.getTermsVersion());
    }
}
