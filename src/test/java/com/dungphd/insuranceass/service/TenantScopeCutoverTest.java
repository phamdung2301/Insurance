package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.AgentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * SaaS Phase 2 Track B — ma trận scope/trạng thái ở PermissionService:
 * fail-closed agent chưa gán tenant, SUSPENDED chỉ đọc, CLOSED chặn cả
 * đọc của nhân viên, CUSTOMER không bị ảnh hưởng, dual-role tách hai phía.
 */
@ExtendWith(MockitoExtension.class)
class TenantScopeCutoverTest {

    @Mock
    private AgentRepository agentRepository;

    private PermissionService permissionService() {
        return new PermissionService(agentRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    private void stubTenantStatus(String tenantId, AgentStatus status) {
        when(agentRepository.findById(tenantId)).thenReturn(Optional.of(
                Agent.builder().id(tenantId).status(status).build()));
    }

    private Policy policy(String agentId, String insuredEmail) {
        return Policy.builder()
                .policyNumber("POL-T")
                .agentId(agentId)
                .insured(Insured.builder().email(insuredEmail).build())
                .build();
    }

    // ---- Mục 2: fail-closed agent chưa gán tenant ----

    @Test
    void pureAgent_withoutTenant_listScope_throwsDistinctUnassigned() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService().applySearchScope(PolicySearchCriteria.builder().build()));
        assertEquals(PermissionService.MSG_TENANT_UNASSIGNED, ex.getMessage());
    }

    @Test
    void pureAgent_withoutTenant_singleRead_returnsFalse() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        // Policy legacy chỉ có email cũ, chưa có agentId: agent không còn
        // thấy qua email cũ nữa (cutover) — fail-closed, không fallback.
        assertFalse(permissionService().canReadPolicy(
                Policy.builder().assignedAgentEmail("agent@example.com").build()));
    }

    @Test
    void pureAgent_withTenant_listScope_forcesTenantId() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.ACTIVE);
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        permissionService().applySearchScope(criteria);
        assertEquals("tenant-1", criteria.getAgentId());
        assertNull(criteria.getAssignedAgentEmail());
        assertFalse(criteria.isDualRoleOrScope());
    }

    // ---- Mục 3: dual-role OR ----

    @Test
    void dualRole_scope_isOr_notAnd() {
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.ACTIVE);
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        permissionService().applySearchScope(criteria);
        assertTrue(criteria.isDualRoleOrScope());
        assertEquals("agent@example.com", criteria.getInsuredEmail());
        assertEquals("tenant-1", criteria.getAgentId());
    }

    @Test
    void dualRole_withoutTenant_scopeKeepsCustomerSideOnly() {
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        // Không throw: phía CUSTOMER vẫn xem được hợp đồng của chính mình,
        // chỉ phía AGENT bị lược (fail-closed phía agent).
        assertDoesNotThrow(() -> permissionService().applySearchScope(criteria));
        assertEquals("agent@example.com", criteria.getInsuredEmail());
        assertNull(criteria.getAgentId());
        assertFalse(criteria.isDualRoleOrScope());
    }

    @Test
    void dualRole_closedTenant_scopeKeepsCustomerSideOnly() {
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.CLOSED);
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        assertDoesNotThrow(() -> permissionService().applySearchScope(criteria));
        assertEquals("agent@example.com", criteria.getInsuredEmail());
        assertNull(criteria.getAgentId());
        assertFalse(criteria.isDualRoleOrScope());
    }

    // ---- Mục 5: SUSPENDED chỉ đọc, CLOSED chặn cả đọc ----

    @Test
    void suspendedTenant_staffReadAllowed_staffWriteBlocked() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.SUSPENDED);
        PermissionService ps = permissionService();
        assertDoesNotThrow(ps::checkTenantReadForStaff);
        ForbiddenException ex = assertThrows(ForbiddenException.class, ps::checkTenantWriteForStaff);
        assertEquals(PermissionService.MSG_TENANT_SUSPENDED_WRITE, ex.getMessage());
    }

    @Test
    void closedTenant_staffReadAndWriteBlockedWithDistinctMessages() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.CLOSED);
        PermissionService ps = permissionService();
        ForbiddenException readEx = assertThrows(ForbiddenException.class, ps::checkTenantReadForStaff);
        assertEquals(PermissionService.MSG_TENANT_CLOSED_READ, readEx.getMessage());
        ForbiddenException writeEx = assertThrows(ForbiddenException.class, ps::checkTenantWriteForStaff);
        assertEquals(PermissionService.MSG_TENANT_CLOSED_WRITE, writeEx.getMessage());
    }

    @Test
    void suspendedTenant_policyWriteBlocked_butCustomerSelfWriteAllowed() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.SUSPENDED);
        PermissionService ps = permissionService();
        // Nhân viên sửa policy của tenant mình → chặn (SUSPENDED).
        assertThrows(ForbiddenException.class,
                () -> ps.checkPolicyWrite(policy("tenant-1", "customer@example.com")));

        // Phía CUSTOMER của dual-role sửa hợp đồng của chính mình → không
        // bị guard trạng thái tenant chặn (dù tenant đang SUSPENDED).
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        assertDoesNotThrow(() -> ps.checkPolicyWrite(policy("tenant-1", "agent@example.com")));
    }

    @Test
    void closedTenant_staffCannotRead_butInsuredSelfCan() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.CLOSED);
        PermissionService ps = permissionService();
        assertFalse(ps.canReadPolicy(policy("tenant-1", "customer@example.com")));
        // Khách không bị khóa oan vì đại lý đóng: hợp đồng của chính mình
        // vẫn đọc được (phía CUSTOMER của dual-role).
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        assertTrue(ps.canReadPolicy(policy("tenant-2", "agent@example.com")));
    }

    @Test
    void closedTenant_staffTransitionBlockedWithClosedMessage() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.CLOSED);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService().checkTransition(
                        policy("tenant-1", "customer@example.com"),
                        PolicyStatus.DRAFT, PolicyStatus.QUOTED));
        assertEquals(PermissionService.MSG_TENANT_CLOSED_WRITE, ex.getMessage());
    }

    // ---- Bypass nền tảng + CUSTOMER không bị ảnh hưởng ----

    @Test
    void adminAndUnderwriter_bypassTenantStatusGuards() {
        // Không stub trạng thái tenant: bypass nghĩa là guard không bao giờ
        // hỏi repo (stub thừa sẽ bị strict-stubs bắt — đúng như mong đợi).
        TenantContext.setTenantId("tenant-1");
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        PermissionService ps = permissionService();
        assertDoesNotThrow(ps::checkTenantReadForStaff);
        assertDoesNotThrow(ps::checkTenantWriteForStaff);
        assertTrue(ps.canReadPolicy(policy("tenant-1", "customer@example.com")));

        authenticateAs("uw@example.com", "ROLE_UNDERWRITER");
        assertDoesNotThrow(ps::checkTenantReadForStaff);
        assertDoesNotThrow(ps::checkTenantWriteForStaff);
    }

    @Test
    void pureCustomer_readOwnPolicy_unaffectedByTenantStatus() {
        authenticateAs("customer@example.com", "ROLE_CUSTOMER");
        TenantContext.setTenantId("tenant-1");
        stubTenantStatus("tenant-1", AgentStatus.CLOSED);
        PermissionService ps = permissionService();
        assertTrue(ps.canReadPolicy(policy("tenant-1", "customer@example.com")));
        assertFalse(ps.canReadPolicy(policy("tenant-1", "other@example.com")));
    }

    @Test
    void demandSide_isTenantMembership_notLegacyEmail() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        PermissionService ps = permissionService();
        assertTrue(ps.isDemandSide(policy("tenant-1", "customer@example.com"), "agent@example.com"));
        assertFalse(ps.isDemandSide(policy("tenant-2", "customer@example.com"), "agent@example.com"));
        assertFalse(ps.isDemandSide(
                Policy.builder().assignedAgentEmail("agent@example.com").build(), "agent@example.com"));
    }
}
