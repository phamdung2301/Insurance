package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Nguồn sự thật cho kiểm tra phạm vi theo role (PLAN §1.3, §1.4).
 * Mọi check đọc email + roles từ SecurityContext (JWT đã lookup DB ở filter),
 * KHÔNG tin header X-User-Email hay query param ?email=.
 *
 * FAIL-CLOSED (đợt 1A): không có principal hợp lệ (không có authentication,
 * anonymous) thì mọi check đều từ chối — canReadPolicy trả false, các hàm
 * check* và applySearchScope throw ForbiddenException. Không còn đường bỏ qua
 * cho "gọi nội bộ/test" trong service này: caller nội bộ nào cần gọi các hàm
 * này phải tự set SecurityContext hợp lệ, hoặc tách luồng riêng không qua
 * check (xem báo cáo đợt 1A: hiện không có caller nội bộ nào như vậy —
 * DataInitializer không gọi PermissionService, mọi caller trong main đều đi
 * qua HTTP và production luôn inject bean thật).
 */
@Slf4j
@Service
public class PermissionService {

    public String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || auth.getName().isBlank() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    public List<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Collections.emptyList();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    public boolean hasRole(String role) {
        return currentRoles().contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ROLE_ADMIN.name());
    }

    public boolean isUnderwriter() {
        return hasRole(Role.ROLE_UNDERWRITER.name());
    }

    public boolean isFinance() {
        return hasRole(Role.ROLE_FINANCE.name());
    }

    public boolean hasCustomerRole() {
        List<String> roles = currentRoles();
        return roles.contains(Role.ROLE_CUSTOMER.name()) || roles.contains(Role.ROLE_USER.name());
    }

    public boolean hasAgentRole() {
        return hasRole(Role.ROLE_AGENT.name());
    }

    private static boolean emailEquals(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /** Phe cầu theo QUAN HỆ với policy, không theo membership role (PLAN §2.3). */
    public boolean isDemandSide(Policy policy, String principal) {
        if (policy == null || principal == null) {
            return false;
        }
        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        return emailEquals(insuredEmail, principal) || emailEquals(policy.getAssignedAgentEmail(), principal);
    }

    public boolean canReadPolicy(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (isAdmin() || isUnderwriter() || isFinance()) {
            return true;
        }
        return isDemandSide(policy, principal);
    }

    public void checkPolicyRead(Policy policy) {
        if (!canReadPolicy(policy)) {
            throw new ForbiddenException("Bạn không có quyền xem hợp đồng này.");
        }
    }

    /** Ghi (sửa/xóa/location/coverage/endorsement): ADMIN mọi policy, CUSTOMER/AGENT trong phạm vi. UW/FINANCE không ghi. Không principal → 403 (fail-closed). */
    public void checkPolicyWrite(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền sửa hợp đồng này.");
        }
        if (isAdmin() || isDemandSide(policy, principal)) {
            return;
        }
        throw new ForbiddenException("Bạn không có quyền sửa hợp đồng này.");
    }

    /** Đọc negotiation: phe cầu trong phạm vi, UNDERWRITER, ADMIN (xem). FINANCE không có quyền ở bảng §1.3. Không principal → 403 (fail-closed). */
    public void checkNegotiationRead(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem thương lượng của hợp đồng này.");
        }
        if (isAdmin() || isUnderwriter() || isDemandSide(policy, principal)) {
            return;
        }
        throw new ForbiddenException("Bạn không có quyền xem thương lượng của hợp đồng này.");
    }

    /** ADMIN write negotiation luôn 403, kể cả khi là chủ policy (PLAN §2.1). */
    public void checkNegotiationWriteNotAdmin() {
        if (isAdmin()) {
            throw new ForbiddenException("Tài khoản ADMIN chỉ được xem thương lượng, không được đề xuất/chốt giá.");
        }
    }

    /**
     * Bảng chuyển trạng thái → role (PLAN §1.6), enforce ở service.
     * Không có principal → 403 với thông điệp chuyển trạng thái như cũ
     * (fail-closed). Principal có nhưng không có role nào cũng bị từ chối
     * qua nhánh allowed=false bên dưới (không còn đường bỏ qua).
     */
    public void checkTransition(Policy policy, PolicyStatus from, PolicyStatus to) {
        String principal = currentPrincipal();
        List<String> roles = currentRoles();
        if (principal == null) {
            throw new ForbiddenException("Vai trò của bạn không được phép chuyển trạng thái từ " + from + " sang " + to + ".");
        }
        if (isAdmin()) {
            return;
        }
        boolean demandAllowed = isDemandSide(policy, principal) && (hasCustomerRole() || hasAgentRole());
        boolean allowed;
        if (from == PolicyStatus.BOUND && to == PolicyStatus.ACTIVE) {
            allowed = isFinance();
        } else if (from == PolicyStatus.BOUND && to == PolicyStatus.DRAFT) {
            allowed = false; // chỉ ADMIN (đã return ở trên)
        } else if (from == PolicyStatus.ACTIVE && to == PolicyStatus.EXPIRED) {
            allowed = false; // chỉ ADMIN
        } else if ((from == PolicyStatus.DRAFT && to == PolicyStatus.QUOTED)
                || (from == PolicyStatus.QUOTED && (to == PolicyStatus.BOUND || to == PolicyStatus.DRAFT))
                || (from == PolicyStatus.BOUND && to == PolicyStatus.CANCELLED)
                || (from == PolicyStatus.ACTIVE && to == PolicyStatus.CANCELLED)) {
            allowed = demandAllowed;
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new ForbiddenException("Vai trò của bạn không được phép chuyển trạng thái từ " + from + " sang " + to + ".");
        }
    }

    /**
     * Ép criteria theo role cho GET /policies (PLAN §1.4): CUSTOMER ép insuredEmail,
     * AGENT ép assignedAgentEmail (bỏ qua giá trị client gửi), UW/FINANCE/ADMIN giữ nguyên.
     * Quyết định khi plan mơ hồ: user mang đồng thời CUSTOMER + AGENT thì ép theo
     * CUSTOMER (an toàn, không lộ policy ngoài phạm vi) — xem báo cáo cuối.
     * Không có principal → 403 (fail-closed): không được liệt kê policy khi chưa
     * xác thực, thay vì trả về danh sách không bị ép phạm vi như trước.
     */
    public void applySearchScope(PolicySearchCriteria criteria) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem danh sách hợp đồng.");
        }
        if (criteria == null) {
            return;
        }
        if (isAdmin() || isUnderwriter() || isFinance()) {
            return;
        }
        String email = principal.trim().toLowerCase();
        if (hasCustomerRole()) {
            criteria.setInsuredEmail(email);
        } else if (hasAgentRole()) {
            criteria.setAssignedAgentEmail(email);
        } else {
            // Role lạ/không xác định: ép về insuredEmail để mặc định không lộ dữ liệu.
            criteria.setInsuredEmail(email);
        }
    }
}
