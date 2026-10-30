package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Đợt 1A, L1 fail-closed: không có principal hợp lệ thì mọi check đều từ chối.
 * Mỗi test tự set/clear SecurityContextHolder — KHÔNG dựa vào fail-open cũ.
 */
class PermissionServiceTest {

    private final PermissionService permissionService = new PermissionService();

    private Policy ownedByCustomer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ownedByCustomer = Policy.builder()
                .policyNumber("POL-1")
                .insured(Insured.builder().email("owner@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    // ---- Không có security context: fail-closed ----

    @Test
    void noContext_canReadPolicy_returnsFalse() {
        assertFalse(permissionService.canReadPolicy(ownedByCustomer));
    }

    @Test
    void noContext_checkPolicyRead_throwsForbidden() {
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService.checkPolicyRead(ownedByCustomer));
        assertEquals("Bạn không có quyền xem hợp đồng này.", ex.getMessage());
    }

    @Test
    void noContext_checkPolicyWrite_throwsForbidden() {
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService.checkPolicyWrite(ownedByCustomer));
        assertEquals("Bạn không có quyền sửa hợp đồng này.", ex.getMessage());
    }

    @Test
    void noContext_checkNegotiationRead_throwsForbidden() {
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService.checkNegotiationRead(ownedByCustomer));
        assertEquals("Bạn không có quyền xem thương lượng của hợp đồng này.", ex.getMessage());
    }

    @Test
    void noContext_checkTransition_throwsForbiddenWithTransitionMessage() {
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> permissionService.checkTransition(ownedByCustomer, PolicyStatus.DRAFT, PolicyStatus.QUOTED));
        assertTrue(ex.getMessage().contains("chuyển trạng thái từ DRAFT sang QUOTED"));
    }

    @Test
    void noContext_applySearchScope_throwsForbidden() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        assertThrows(ForbiddenException.class,
                () -> permissionService.applySearchScope(criteria));
    }

    @Test
    void anonymousUser_canReadPolicy_returnsFalse() {
        authenticateAs("anonymousUser", "ROLE_CUSTOMER");
        assertFalse(permissionService.canReadPolicy(ownedByCustomer));
        assertThrows(ForbiddenException.class,
                () -> permissionService.checkPolicyRead(ownedByCustomer));
    }

    // ---- Đối chứng: có context hợp lệ thì vẫn cho phép đúng phạm vi ----

    @Test
    void adminContext_canReadAndWriteAndSearchUnscoped() {
        authenticateAs("admin@insurtech.vn", "ROLE_ADMIN");
        assertTrue(permissionService.canReadPolicy(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkPolicyRead(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkPolicyWrite(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkNegotiationRead(ownedByCustomer));

        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        assertDoesNotThrow(() -> permissionService.applySearchScope(criteria));
        assertNull(criteria.getInsuredEmail());
        assertNull(criteria.getAssignedAgentEmail());
    }

    @Test
    void customerOwner_canReadButStrangerCannot() {
        authenticateAs("owner@example.com", "ROLE_CUSTOMER");
        assertTrue(permissionService.canReadPolicy(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkPolicyRead(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkPolicyWrite(ownedByCustomer));
        assertDoesNotThrow(() -> permissionService.checkTransition(
                ownedByCustomer, PolicyStatus.DRAFT, PolicyStatus.QUOTED));

        authenticateAs("stranger@example.com", "ROLE_CUSTOMER");
        assertFalse(permissionService.canReadPolicy(ownedByCustomer));
        assertThrows(ForbiddenException.class,
                () -> permissionService.checkPolicyRead(ownedByCustomer));
        assertThrows(ForbiddenException.class,
                () -> permissionService.checkPolicyWrite(ownedByCustomer));
    }

    @Test
    void customerSearchScope_forcesOwnInsuredEmail() {
        authenticateAs("owner@example.com", "ROLE_CUSTOMER");
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .insuredEmail("someone-else@example.com")
                .build();
        permissionService.applySearchScope(criteria);
        assertEquals("owner@example.com", criteria.getInsuredEmail());
    }

    @Test
    void agentSearchScope_forcesOwnAssignedEmail() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        PolicySearchCriteria criteria = PolicySearchCriteria.builder().build();
        permissionService.applySearchScope(criteria);
        assertEquals("agent@example.com", criteria.getAssignedAgentEmail());
    }

    @Test
    void financeContext_canApproveBoundToActive() {
        authenticateAs("finance@example.com", "ROLE_FINANCE");
        assertDoesNotThrow(() -> permissionService.checkTransition(
                ownedByCustomer, PolicyStatus.BOUND, PolicyStatus.ACTIVE));
    }
}
