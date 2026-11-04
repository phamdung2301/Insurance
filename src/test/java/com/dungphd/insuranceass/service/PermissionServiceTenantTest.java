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
 * SaaS Phase 2 Track B: scope theo tenant trong PermissionService.
 * (File này gốc từ Phase 1 chỉ test helper đọc tenant; từ Track B nó khóa
 * luôn hành vi cutover — xem test tenantScope_cutover_*.)
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

    // THAY ĐỔI HÀNH VI CÓ CHỦ ĐÍCH (SaaS Phase 2 Track B — scope cutover):
    // test Phase 1 "tenantHelper_doesNotChangeExistingScope" khóa hành vi
    // scope email cũ; từ Phase 2 scope chạy theo tenant nên agent của
    // tenant-1 ĐỌC ĐƯỢC policy của tenant-1 dù assignedAgentEmail khác,
    // và KHÔNG đọc được policy tenant khác dù email cũ có trùng.
    @Test
    void tenantScope_cutover_readsByTenantNotEmail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("agent1@example.com", null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AGENT"))));
        TenantContext.setTenantId("tenant-1");
        Policy ownTenant = Policy.builder()
                .assignedAgentEmail("agent2@example.com")
                .agentId("tenant-1")
                .build();
        assertTrue(permissionService.canReadPolicy(ownTenant));
        Policy foreignTenant = Policy.builder()
                .assignedAgentEmail("agent1@example.com")
                .agentId("tenant-2")
                .build();
        assertFalse(permissionService.canReadPolicy(foreignTenant));
        Policy unassigned = Policy.builder()
                .assignedAgentEmail("agent1@example.com")
                .agentId(null)
                .build();
        assertFalse(permissionService.canReadPolicy(unassigned));
    }
}
