package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.AdminUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminAuditLogResponse;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.AdminAuditLog;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.AdminUserService;
import com.dungphd.insuranceass.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            Role.ROLE_CUSTOMER.name(),
            Role.ROLE_AGENT.name(),
            Role.ROLE_UNDERWRITER.name(),
            Role.ROLE_FINANCE.name(),
            Role.ROLE_ADMIN.name(),
            Role.ROLE_USER.name()
    );

    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    // Nối hook mở khóa password (đợt 1, L5): admin mở khóa tài khoản thì đồng thời
    // xóa khóa password tạm (5 lần sai) trong OtpService. OtpService không phụ thuộc
    // ngược lại service này nên không tạo vòng tròn bean.
    private final OtpService otpService;

    @Override
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AdminUserResponse createUser(AdminUserCreateRequest request, String actor) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User with email " + email + " already exists");
        }
        List<String> roles = normalizeRoles(request.getRoles());
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(new ArrayList<>(roles))
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        User saved = userRepository.save(user);
        writeAudit(actor, email, "CREATE_USER", List.of(), roles);
        return mapToResponse(saved);
    }

    @Override
    public AdminUserResponse updateRoles(String userId, List<String> roles, String actor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        List<String> before = user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>();
        List<String> after = normalizeRoles(roles);
        // Guard (a): admin không được tự đổi vai trò của chính mình.
        if (isSelf(actor, user.getEmail()) && !sameRoles(before, after)) {
            writeAudit(actor, user.getEmail(), "BLOCKED_SELF_ROLE_CHANGE", before, before);
            throw new ForbiddenException("Không thể tự thay đổi vai trò của chính mình.");
        }
        // Guard (c): không được tước ROLE_ADMIN khỏi admin đang hoạt động cuối cùng.
        if (isActiveAdmin(user) && !after.contains(Role.ROLE_ADMIN.name())
                && countOtherActiveAdmins(user) == 0) {
            writeAudit(actor, user.getEmail(), "BLOCKED_LAST_ADMIN_REMOVAL", before, before);
            throw new ForbiddenException("Không thể vô hiệu hóa admin cuối cùng còn hoạt động.");
        }
        user.setRoles(new ArrayList<>(after));
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        writeAudit(actor, saved.getEmail(), "UPDATE_ROLES", before, after);
        return mapToResponse(saved);
    }

    @Override
    public AdminUserResponse setEnabled(String userId, boolean enabled, String actor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        List<String> roles = user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>();
        // Guard (b): admin không được tự khóa chính mình.
        if (!enabled && isSelf(actor, user.getEmail())) {
            writeAudit(actor, user.getEmail(), "BLOCKED_SELF_LOCK", roles, roles);
            throw new ForbiddenException("Không thể tự khóa tài khoản của chính mình.");
        }
        // Guard (c): không được khóa admin đang hoạt động cuối cùng.
        if (!enabled && isActiveAdmin(user) && countOtherActiveAdmins(user) == 0) {
            writeAudit(actor, user.getEmail(), "BLOCKED_LAST_ADMIN_REMOVAL", roles, roles);
            throw new ForbiddenException("Không thể vô hiệu hóa admin cuối cùng còn hoạt động.");
        }
        user.setEnabled(enabled);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        if (enabled) {
            otpService.clearPasswordLockout(saved.getEmail());
        }
        writeAudit(actor, saved.getEmail(), enabled ? "UNLOCK_ACCOUNT" : "LOCK_ACCOUNT", roles, roles);
        return mapToResponse(saved);
    }

    /**
     * Actor có phải chính là target không. So sánh email sau trim + lowercase.
     * Actor null/blank/"System" (luồng seed) không có "chính mình" nên luôn false.
     */
    private boolean isSelf(String actor, String targetEmail) {
        if (actor == null || actor.isBlank() || "System".equalsIgnoreCase(actor.trim())) {
            return false;
        }
        if (targetEmail == null) {
            return false;
        }
        return actor.trim().equalsIgnoreCase(targetEmail.trim());
    }

    /** So sánh tập vai trò, không phân biệt thứ tự và hoa/thường. */
    private boolean sameRoles(List<String> a, List<String> b) {
        Set<String> normA = a.stream().map(r -> r == null ? "" : r.trim().toUpperCase())
                .collect(Collectors.toSet());
        Set<String> normB = b.stream().map(r -> r == null ? "" : r.trim().toUpperCase())
                .collect(Collectors.toSet());
        return normA.equals(normB);
    }

    /** Admin "đang hoạt động": có ROLE_ADMIN và không bị khóa (enabled khác false). */
    private boolean isActiveAdmin(User user) {
        return user.getRoles() != null
                && user.getRoles().contains(Role.ROLE_ADMIN.name())
                && !Boolean.FALSE.equals(user.getEnabled());
    }

    /**
     * Đếm số admin đang hoạt động khác target. Dùng findAll() vì UserRepository
     * hiện không có phương thức đếm chuyên biệt (không thêm method repo ngoài
     * phạm vi file cho phép); tập user quản trị nhỏ nên quét toàn bộ là đủ.
     */
    private long countOtherActiveAdmins(User target) {
        return userRepository.findAll().stream()
                .filter(u -> !isSameUser(u, target))
                .filter(this::isActiveAdmin)
                .count();
    }

    private boolean isSameUser(User a, User b) {
        if (a.getId() != null && b.getId() != null) {
            return a.getId().equals(b.getId());
        }
        if (a.getEmail() != null && b.getEmail() != null) {
            return a.getEmail().trim().equalsIgnoreCase(b.getEmail().trim());
        }
        return a == b;
    }

    @Override
    public List<AdminAuditLogResponse> listAuditLogs() {
        return auditLogRepository.findAllNewestFirst().stream()
                .map(log -> AdminAuditLogResponse.builder()
                        .id(log.getId())
                        .actorEmail(log.getActorEmail())
                        .targetEmail(log.getTargetEmail())
                        .action(log.getAction())
                        .rolesBefore(log.getRolesBefore())
                        .rolesAfter(log.getRolesAfter())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BadRequestException("roles không được rỗng.");
        }
        List<String> normalized = roles.stream()
                .map(r -> r == null ? "" : r.trim().toUpperCase())
                .collect(Collectors.toList());
        for (String role : normalized) {
            if (!ALLOWED_ROLES.contains(role)) {
                throw new BadRequestException("Role không hợp lệ: " + role);
            }
        }
        return normalized;
    }

    private void writeAudit(String actor, String targetEmail, String action, List<String> before, List<String> after) {
        auditLogRepository.save(AdminAuditLog.builder()
                .actorEmail(actor != null && !actor.isBlank() ? actor.trim() : "System")
                .targetEmail(targetEmail)
                .action(action)
                .rolesBefore(new ArrayList<>(before))
                .rolesAfter(new ArrayList<>(after))
                .createdAt(Instant.now())
                .build());
    }

    private AdminUserResponse mapToResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
