package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.model.Policy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS Phase 1: helper đọc tenant trong PermissionService — chỉ là helper để
 * service dùng từ Phase 2. Test này KHÔNG assert bất kỳ thay đổi scope nào:
 * scope Phase 1 vẫn chạy đúng như cũ (xem PermissionServiceTest).
 */
class PermissionServiceTenantTest {

    private final PermissionService permissionService = new PermissionService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void currentTenantId_readsTenantContext() {
        assertNull(permissionService.currentTenantId());
        TenantContext.setTenantId("tenant-1");
        assertEquals("tenant-1", permissionService.currentTenantId());
    }

    @Test
    void isTenantMember_matchesOnlyOwnTenant() {
        TenantContext.setTenantId("tenant-1");
        assertTrue(permissionService.isTenantMember(Policy.builder().agentId("tenant-1").build()));
        assertFalse(permissionService.isTenantMember(Policy.builder().agentId("tenant-2").build()));
        assertFalse(permissionService.isTenantMember(Policy.builder().agentId(null).build()));
        assertFalse(permissionService.isTenantMember(null));

        TenantContext.clear();
        assertFalse(permissionService.isTenantMember(Policy.builder().agentId("tenant-1").build()));
    }

    @Test
    void tenantHelper_doesNotChangeExistingScope() {
        // Agent của tenant-1 vẫn bị scope cũ chặn khi đọc policy của agent khác
        // theo email (Phase 1 chưa đổi scope sang tenant) — khóa hành vi cũ.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("agent1@example.com", null, List.of()));
        Policy foreign = Policy.builder()
                .assignedAgentEmail("agent2@example.com")
                .agentId("tenant-1")
                .build();
        assertFalse(permissionService.canReadPolicy(foreign));
    }
}
