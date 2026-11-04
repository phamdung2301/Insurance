package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.AgentTenantUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.AdminAuditLog;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.AgentTenantUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 2 Track B mục 4 + Q8: quản lý user theo tenant của agent
 * (GET/POST /agent/users) và khóa/mở CUSTOMER (PUT /agent/users/{id}/enabled).
 * Dùng PermissionService THẬT (gắn AgentRepository mock theo trạng thái
 * tenant) để guard fail-closed / SUSPENDED / CLOSED được kiểm chứng cùng
 * lúc với logic service.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AgentTenantUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private OtpService otpService;

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

    private AgentTenantUserServiceImpl service() {
        return new AgentTenantUserServiceImpl(
                userRepository, auditLogRepository, passwordEncoder,
                new PermissionService(agentRepository), otpService);
    }

    private void asTenantAgent(String tenantId, AgentStatus status) {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId(tenantId);
        when(agentRepository.findById(tenantId)).thenReturn(Optional.of(
                Agent.builder().id(tenantId).status(status).build()));
    }

    private User user(String id, String email, List<String> roles, String agentId) {
        return User.builder().id(id).email(email).roles(roles).agentId(agentId).enabled(true).build();
    }

    // ---- GET /agent/users ----

    @Test
    void list_returnsOnlyOwnTenantUsers() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(
                user("u1", "c1@example.com", List.of("ROLE_CUSTOMER"), "tenant-1"),
                user("u2", "c2@example.com", List.of("ROLE_CUSTOMER"), "tenant-2"),
                user("u3", "c3@example.com", List.of("ROLE_CUSTOMER"), null)));
        List<AdminUserResponse> result = service().listTenantUsers();
        assertEquals(List.of("c1@example.com"),
                result.stream().map(AdminUserResponse::getEmail).toList());
    }

    @Test
    void list_adminCaller_forbidden() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        assertThrows(ForbiddenException.class, () -> service().listTenantUsers());
    }

    @Test
    void list_unassignedAgent_forbiddenFailClosed() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service().listTenantUsers());
        assertTrue(ex.getMessage().contains("chưa được gán tenant"), ex.getMessage());
    }

    @Test
    void list_closedTenant_forbidden_butSuspendedTenant_readAllowed() {
        asTenantAgent("tenant-1", AgentStatus.CLOSED);
        when(userRepository.findAll()).thenReturn(List.of());
        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service().listTenantUsers());
        assertTrue(ex.getMessage().contains("CLOSED"), ex.getMessage());

        asTenantAgent("tenant-1", AgentStatus.SUSPENDED);
        assertDoesNotThrow(() -> service().listTenantUsers());
    }

    // ---- POST /agent/users ----

    @Test
    void create_forcesCustomerRoleAndTenant_andAudits() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse result = service().createTenantUser(
                AgentTenantUserCreateRequest.builder()
                        .email("New@Example.com").password("pw").fullName("Khách Mới").build(),
                "agent@example.com");

        assertEquals(List.of("ROLE_CUSTOMER"), result.getRoles());
        assertEquals("tenant-1", result.getAgentId());
        assertEquals("new@example.com", result.getEmail());
        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals("AGENT_CREATE_USER", audit.getValue().getAction());
        assertEquals("agent@example.com", audit.getValue().getActorEmail());
    }

    @Test
    void create_suspendedTenant_forbidden_andWritesNothing() {
        asTenantAgent("tenant-1", AgentStatus.SUSPENDED);
        assertThrows(ForbiddenException.class, () -> service().createTenantUser(
                AgentTenantUserCreateRequest.builder()
                        .email("new@example.com").password("pw").build(),
                "agent@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_duplicateEmail_rejected() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service().createTenantUser(
                AgentTenantUserCreateRequest.builder()
                        .email("dup@example.com").password("pw").build(),
                "agent@example.com"));
    }

    // ---- PUT /agent/users/{id}/enabled (Q8) ----

    @Test
    void setEnabled_sameTenantCustomer_locksAndAudits() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        User target = user("u1", "c1@example.com", List.of("ROLE_CUSTOMER"), "tenant-1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse result = service().setTenantUserEnabled("u1", false, "agent@example.com");
        assertFalse(result.getEnabled());
        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals("AGENT_LOCK_ACCOUNT", audit.getValue().getAction());
        assertEquals("c1@example.com", audit.getValue().getTargetEmail());
    }

    @Test
    void setEnabled_unlock_clearsPasswordLockout() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        User target = user("u1", "c1@example.com", List.of("ROLE_CUSTOMER"), "tenant-1");
        target.setEnabled(false);
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse result = service().setTenantUserEnabled("u1", true, "agent@example.com");
        assertTrue(result.getEnabled());
        verify(otpService).clearPasswordLockout("c1@example.com");
        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertEquals("AGENT_UNLOCK_ACCOUNT", audit.getValue().getAction());
    }

    @Test
    void setEnabled_foreignTenant_forbidden() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        when(userRepository.findById("u2")).thenReturn(Optional.of(
                user("u2", "c2@example.com", List.of("ROLE_CUSTOMER"), "tenant-2")));
        assertThrows(ForbiddenException.class,
                () -> service().setTenantUserEnabled("u2", false, "agent@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_nonCustomer_forbidden() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        when(userRepository.findById("u9")).thenReturn(Optional.of(
                user("u9", "staff@example.com", List.of("ROLE_AGENT"), "tenant-1")));
        assertThrows(ForbiddenException.class,
                () -> service().setTenantUserEnabled("u9", false, "agent@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_self_forbidden() {
        asTenantAgent("tenant-1", AgentStatus.ACTIVE);
        // Tài khoản dual-role mang email của agent nhưng role CUSTOMER:
        // guard tự khóa vẫn chặn.
        when(userRepository.findById("u0")).thenReturn(Optional.of(
                user("u0", "agent@example.com", List.of("ROLE_CUSTOMER"), "tenant-1")));
        assertThrows(ForbiddenException.class,
                () -> service().setTenantUserEnabled("u0", false, "agent@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_adminCaller_forbidden() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        assertThrows(ForbiddenException.class,
                () -> service().setTenantUserEnabled("u1", false, "admin@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_suspendedTenant_forbidden() {
        asTenantAgent("tenant-1", AgentStatus.SUSPENDED);
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                user("u1", "c1@example.com", List.of("ROLE_CUSTOMER"), "tenant-1")));
        assertThrows(ForbiddenException.class,
                () -> service().setTenantUserEnabled("u1", false, "agent@example.com"));
        verify(userRepository, never()).save(any());
    }
}
