package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.AgentTenantUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.AdminAuditLog;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.AgentTenantUserService;
import com.dungphd.insuranceass.service.OtpService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản lý user theo tenant cho ĐẠI LÝ — SaaS Phase 2 Track B mục 4 + Q8.
 *
 * <p>Audit theo đúng pattern hiện có của codebase
 * ({@code AdminUserServiceImpl.writeAudit}): ghi {@code AdminAuditLog} với
 * actorEmail / targetEmail / action / rolesBefore / rolesAfter /
 * createdAt. Action mới của track này: {@code AGENT_CREATE_USER},
 * {@code AGENT_LOCK_ACCOUNT}, {@code AGENT_UNLOCK_ACCOUNT} — không bịa
 * cơ chế audit mới, chỉ thêm action vào cùng một bảng audit ADMIN đã xem
 * được qua /admin/audit-logs.</p>
 *
 * <p>Ma trận chặn (mọi lần chặn đều 403 với message phân biệt, trừ
 * không tìm thấy target thì 404): ADMIN gọi → 403 (dùng endpoint admin
 * riêng); agent chưa gán tenant → 403 fail-closed; tenant SUSPENDED →
 * ghi bị chặn, đọc vẫn được; tenant CLOSED → đọc/ghi đều bị chặn;
 * target không phải CUSTOMER thuần / tenant khác / chính mình → 403.</p>
 */
@Service
@RequiredArgsConstructor
public class AgentTenantUserServiceImpl implements AgentTenantUserService {

    /** Role được phép khóa/mở qua endpoint agent: CUSTOMER thuần (ROLE_USER là tên cũ tương đương). */
    private static final Set<String> LOCKABLE_ROLES = Set.of(
            Role.ROLE_CUSTOMER.name(), Role.ROLE_USER.name());

    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final OtpService otpService;

    @Override
    public List<AdminUserResponse> listTenantUsers() {
        String tenantId = requireAgentCaller();
        permissionService.checkTenantReadForStaff();
        return userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getAgentId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AdminUserResponse createTenantUser(AgentTenantUserCreateRequest request, String actor) {
        String tenantId = requireAgentCaller();
        // Ghi của nhân viên tenant: fail-closed + SUSPENDED/CLOSED chặn.
        permissionService.checkTenantWriteForStaff();
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Đã tồn tại tài khoản với email " + email + ".");
        }
        // Ép cứng: ROLE_CUSTOMER duy nhất + tenant từ context — request
        // không có field roles nên không có đường nào tạo role khác.
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(new ArrayList<>(List.of(Role.ROLE_CUSTOMER.name())))
                .agentId(tenantId)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        User saved = userRepository.save(user);
        writeAudit(actor, email, "AGENT_CREATE_USER", List.of(), List.of(Role.ROLE_CUSTOMER.name()));
        return mapToResponse(saved);
    }

    @Override
    public AdminUserResponse setTenantUserEnabled(String userId, boolean enabled, String actor) {
        String tenantId = requireAgentCaller();
        // Khóa/mở là ghi của nhân viên tenant → SUSPENDED/CLOSED chặn.
        permissionService.checkTenantWriteForStaff();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
        List<String> roles = target.getRoles() != null ? new ArrayList<>(target.getRoles()) : new ArrayList<>();
        // Chặn: khóa tài khoản không phải CUSTOMER thuần (AGENT/UW/ADMIN
        // hoặc đa role dính AGENT/UW/ADMIN đều không khóa được qua đây).
        if (roles.isEmpty() || !LOCKABLE_ROLES.containsAll(roles)) {
            writeAudit(actor, target.getEmail(), "BLOCKED_AGENT_LOCK_NON_CUSTOMER", roles, roles);
            throw new ForbiddenException("Chỉ được khóa/mở tài khoản CUSTOMER thuộc tenant của mình.");
        }
        // Chặn: khóa customer tenant khác.
        if (!tenantId.equals(target.getAgentId())) {
            writeAudit(actor, target.getEmail(), "BLOCKED_AGENT_LOCK_FOREIGN_TENANT", roles, roles);
            throw new ForbiddenException("Chỉ được khóa/mở tài khoản CUSTOMER thuộc tenant của mình.");
        }
        // Chặn: khóa chính mình (agent tự khóa tài khoản của mình qua đây
        // cũng bị chặn — dù tài khoản agent vốn không khóa được theo luật
        // CUSTOMER ở trên, guard này giữ cho trường hợp dual-role).
        if (isSelf(actor, target.getEmail())) {
            writeAudit(actor, target.getEmail(), "BLOCKED_SELF_LOCK", roles, roles);
            throw new ForbiddenException("Không thể tự khóa tài khoản của chính mình.");
        }
        target.setEnabled(enabled);
        target.setUpdatedAt(Instant.now());
        User saved = userRepository.save(target);
        if (enabled) {
            // Cùng pattern AdminUserServiceImpl: mở khóa thì xóa khóa
            // password tạm để tài khoản đăng nhập lại được ngay.
            otpService.clearPasswordLockout(saved.getEmail());
        }
        writeAudit(actor, saved.getEmail(), enabled ? "AGENT_UNLOCK_ACCOUNT" : "AGENT_LOCK_ACCOUNT", roles, roles);
        return mapToResponse(saved);
    }

    /**
     * Endpoint này là của AGENT. ADMIN gọi → 403 (ADMIN dùng endpoint
     * /admin/users riêng). Matcher SecurityConfig /agent/** cho ADMIN đi
     * qua, nên service phải chặn lại ở đây — không để lọt scope.
     */
    private String requireAgentCaller() {
        if (permissionService.isAdmin()) {
            throw new ForbiddenException("Endpoint này dành cho đại lý; ADMIN dùng endpoint quản trị /admin/users.");
        }
        if (!permissionService.hasAgentRole()) {
            throw new ForbiddenException("Chỉ đại lý (AGENT) mới được quản lý user theo tenant.");
        }
        return permissionService.requireAgentTenant();
    }

    private boolean isSelf(String actor, String targetEmail) {
        if (actor == null || actor.isBlank() || "System".equalsIgnoreCase(actor.trim())) {
            return false;
        }
        if (targetEmail == null) {
            return false;
        }
        return actor.trim().equalsIgnoreCase(targetEmail.trim());
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
                .agentId(user.getAgentId())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
