package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.AdminAuditLog;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Đợt 1B — guard admin (L3): tự đổi role, tự khóa, admin cuối cùng.
 * Mọi trường hợp bị chặn đều phải ghi audit BLOCKED_* rồi mới throw, và
 * không được gọi userRepository.save().
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AdminUserServiceImpl service;

    private User adminA;
    private User adminB;
    private User customer;

    @BeforeEach
    void setUp() {
        adminA = user("a1", "adminA@example.com", List.of("ROLE_ADMIN"), true);
        adminB = user("a2", "adminB@example.com", List.of("ROLE_ADMIN"), true);
        customer = user("c1", "customer@example.com", List.of("ROLE_CUSTOMER"), true);
    }

    private User user(String id, String email, List<String> roles, boolean enabled) {
        return User.builder()
                .id(id)
                .email(email)
                .password("x")
                .roles(new ArrayList<>(roles))
                .enabled(enabled)
                .build();
    }

    private void stubFindAll(User... users) {
        when(userRepository.findAll()).thenReturn(List.of(users));
    }

    private AdminAuditLog capturedAudit() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void updateRoles_SelfRoleChange_BlockedAndAudited() {
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.updateRoles("a1", List.of("ROLE_AGENT"), "  AdminA@Example.com "));
        assertTrue(ex.getMessage().contains("chính mình"));

        AdminAuditLog log = capturedAudit();
        assertEquals("BLOCKED_SELF_ROLE_CHANGE", log.getAction());
        assertEquals(List.of("ROLE_ADMIN"), log.getRolesBefore());
        assertEquals(List.of("ROLE_ADMIN"), log.getRolesAfter());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_SelfSameRoles_Allowed() {
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.updateRoles("a1", List.of("ROLE_ADMIN"), "adminA@example.com");
        assertEquals(List.of("ROLE_ADMIN"), result.getRoles());
        verify(userRepository, never()).findAll();
    }

    @Test
    void setEnabled_SelfLock_BlockedAndAudited() {
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.setEnabled("a1", false, "admina@example.com"));
        assertTrue(ex.getMessage().contains("chính mình"));

        AdminAuditLog log = capturedAudit();
        assertEquals("BLOCKED_SELF_LOCK", log.getAction());
        assertEquals(List.of("ROLE_ADMIN"), log.getRolesBefore());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_RemoveRoleFromLastActiveAdmin_BlockedAndAudited() {
        // Chỉ có adminA là admin đang hoạt động; adminB ở đây là customer.
        User nonAdminB = user("a2", "adminB@example.com", List.of("ROLE_CUSTOMER"), true);
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));
        stubFindAll(adminA, nonAdminB, customer);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.updateRoles("a1", List.of("ROLE_AGENT"), "adminB@example.com"));
        assertTrue(ex.getMessage().contains("admin cuối cùng"));

        assertEquals("BLOCKED_LAST_ADMIN_REMOVAL", capturedAudit().getAction());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabled_LockLastActiveAdmin_BlockedAndAudited() {
        User nonAdminB = user("a2", "adminB@example.com", List.of("ROLE_CUSTOMER"), true);
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));
        stubFindAll(adminA, nonAdminB, customer);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.setEnabled("a1", false, "adminB@example.com"));
        assertTrue(ex.getMessage().contains("admin cuối cùng"));

        assertEquals("BLOCKED_LAST_ADMIN_REMOVAL", capturedAudit().getAction());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_OtherAdminRemovesAdminRole_WhenAnotherAdminRemains_Allowed() {
        when(userRepository.findById("a2")).thenReturn(Optional.of(adminB));
        stubFindAll(adminA, adminB);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.updateRoles("a2", List.of("ROLE_AGENT"), "adminA@example.com");
        assertEquals(List.of("ROLE_AGENT"), result.getRoles());
    }

    @Test
    void setEnabled_OtherAdminLocksAdmin_WhenAnotherAdminRemains_Allowed() {
        when(userRepository.findById("a2")).thenReturn(Optional.of(adminB));
        stubFindAll(adminA, adminB);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.setEnabled("a2", false, "adminA@example.com");
        assertFalse(result.getEnabled());
    }

    @Test
    void updateRoles_ActorNullOrSystem_SelfGuardSkippedButLastAdminGuardStillApplies() {
        // Actor null: không xác định được "chính mình" nên guard (a) bỏ qua,
        // nhưng tước role admin cuối cùng vẫn bị guard (c) chặn.
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));
        stubFindAll(adminA, customer);

        assertThrows(ForbiddenException.class,
                () -> service.updateRoles("a1", List.of("ROLE_AGENT"), null));
        verify(userRepository, never()).save(any());

        // Actor "System" (luồng seed): đổi role customer bình thường vẫn qua.
        when(userRepository.findById("c1")).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var result = service.updateRoles("c1", List.of("ROLE_AGENT"), "System");
        assertEquals(List.of("ROLE_AGENT"), result.getRoles());
    }

    @Test
    void setEnabled_DisabledAdminIsNotCountedAsActiveAdmin() {
        // adminB đã bị khóa từ trước → adminA là admin hoạt động duy nhất.
        User disabledB = user("a2", "adminB@example.com", List.of("ROLE_ADMIN"), false);
        when(userRepository.findById("a1")).thenReturn(Optional.of(adminA));
        stubFindAll(adminA, disabledB);

        assertThrows(ForbiddenException.class,
                () -> service.setEnabled("a1", false, "someone@example.com"));
        verify(userRepository, never()).save(any());
    }
}
