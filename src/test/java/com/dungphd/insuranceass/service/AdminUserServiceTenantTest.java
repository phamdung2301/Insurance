package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AdminUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.AdminAuditLog;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 2 Track B mục 4 (phía ADMIN): filter agentId ở list users,
 * field agentId lúc tạo user, PUT /admin/users/{id}/agent gán/đổi/thu hồi
 * tenant (ghi audit ASSIGN_TENANT, không đổi Policy.agentId — service này
 * vốn không chạm tới policy nên không có đường nào đổi tenant policy cũ).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AdminUserServiceTenantTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpService otpService;

    @Mock
    private AgentRepository agentRepository;

    private AdminUserServiceImpl service() {
        AdminUserServiceImpl impl = new AdminUserServiceImpl(
                userRepository, auditLogRepository, passwordEncoder, otpService);
        ReflectionTestUtils.setField(impl, "agentRepository", agentRepository);
        return impl;
    }

    private User user(String id, String email, List<String> roles, String agentId) {
        return User.builder().id(id).email(email).roles(roles).agentId(agentId).enabled(true).build();
    }

    @Test
    void list_filterByAgentId_returnsOnlyThatTenant() {
        when(userRepository.findAll()).thenReturn(List.of(
                user("u1", "a@example.com", List.of("ROLE_CUSTOMER"), "tenant-1"),
                user("u2", "b@example.com", List.of("ROLE_CUSTOMER"), "tenant-2"),
                user("u3", "c@example.com", List.of("ROLE_CUSTOMER"), null)));
        List<AdminUserResponse> filtered = service().listUsers("tenant-1");
        assertEquals(List.of("a@example.com"),
                filtered.stream().map(AdminUserResponse::getEmail).toList());
        assertEquals("tenant-1", filtered.get(0).getAgentId());
        assertEquals(3, service().listUsers(null).size());
        assertEquals(3, service().listUsers().size());
    }

    @Test
    void create_withExistingAgentId_setsTenant() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(agentRepository.existsById("tenant-1")).thenReturn(true);
        when(passwordEncoder.encode("pw")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse result = service().createUser(
                AdminUserCreateRequest.builder()
                        .email("new@example.com").password("pw")
                        .roles(List.of("ROLE_CUSTOMER")).agentId("tenant-1").build(),
                "admin@example.com");
        assertEquals("tenant-1", result.getAgentId());
    }

    @Test
    void create_withUnknownAgentId_rejected() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(agentRepository.existsById("tenant-nope")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> service().createUser(
                AdminUserCreateRequest.builder()
                        .email("new@example.com").password("pw")
                        .roles(List.of("ROLE_CUSTOMER")).agentId("tenant-nope").build(),
                "admin@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_agentRoleWithoutAgentId_rejectedPerPlan() {
        // PLAN §3.5: tạo user AGENT phải chọn tenant trước, không sinh
        // tenant ẩn, không để agent "ma" rồi fail-closed lặng sau này.
        when(userRepository.existsByEmail("agent-new@example.com")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> service().createUser(
                AdminUserCreateRequest.builder()
                        .email("agent-new@example.com").password("pw")
                        .roles(List.of("ROLE_AGENT")).build(),
                "admin@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_customerWithoutAgentId_ok() {
        when(userRepository.existsByEmail("c@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        AdminUserResponse result = service().createUser(
                AdminUserCreateRequest.builder()
                        .email("c@example.com").password("pw")
                        .roles(List.of("ROLE_CUSTOMER")).build(),
                "admin@example.com");
        assertNull(result.getAgentId());
    }

    @Test
    void setAgent_assignsChangesAndClears_withAudit() {
        User target = user("u1", "c@example.com", List.of("ROLE_CUSTOMER"), "tenant-1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(target));
        when(agentRepository.existsById("tenant-2")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse changed = service().setAgent("u1", "tenant-2", "admin@example.com");
        assertEquals("tenant-2", changed.getAgentId());
        assertEquals(List.of("ROLE_CUSTOMER"), changed.getRoles());

        AdminUserResponse cleared = service().setAgent("u1", null, "admin@example.com");
        assertNull(cleared.getAgentId());

        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository, times(2)).save(audit.capture());
        assertTrue(audit.getAllValues().stream()
                .allMatch(a -> "ASSIGN_TENANT".equals(a.getAction())));
    }

    @Test
    void setAgent_unknownAgent_rejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                user("u1", "c@example.com", List.of("ROLE_CUSTOMER"), null)));
        when(agentRepository.existsById("tenant-nope")).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> service().setAgent("u1", "tenant-nope", "admin@example.com"));
        verify(userRepository, never()).save(any());
    }
}
