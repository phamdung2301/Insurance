package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.repository.AgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Nguồn sự thật cho kiểm tra phạm vi theo role (PLAN §1.3, §1.4).
 * Mọi check đọc email + roles từ SecurityContext (JWT đã lookup DB ở filter),
 * KHÔNG tin header X-User-Email hay query param ?email=.
 *
 * FAIL-CLOSED: không có principal hợp lệ (không có authentication,
 * anonymous) thì mọi check đều từ chối.
 *
 * <p>SaaS Phase 2 Track B — scope cutover sang tenant (PLAN §3.3, §2.1):</p>
 * <ul>
 *   <li>Phe cầu của agent giờ là <b>membership tenant</b>
 *       ({@code policy.agentId == TenantContext}), KHÔNG còn là
 *       {@code assignedAgentEmail == principal}. Agent chưa gán tenant
 *       (TenantContext null) mất mọi quyền xem/ghi theo tenant và nhận
 *       403 với message phân biệt được — không fallback về scope email cũ,
 *       không trả danh sách rỗng lặng.</li>
 *   <li>Phía CUSTOMER không đổi: khách xem/ghi hợp đồng của chính mình theo
 *       {@code insured.email == principal}, không qua tenant. Tài khoản
 *       dual-role AGENT+CUSTOMER: phía CUSTOMER vẫn xem được hợp đồng của
 *       chính mình, phía AGENT fail-closed khi chưa có tenant.</li>
 *   <li>Trạng thái tenant (PLAN §2.1, quyết Q9): nhân viên tenant
 *       SUSPENDED chỉ đọc (mọi ghi của nhân viên bị chặn); tenant CLOSED:
 *       nhân viên không đọc/ghi (chỉ ADMIN đọc lịch sử). ADMIN/UNDERWRITER
 *       là role nền tảng nên bypass guard trạng thái tenant. Hành động của
 *       CUSTOMER trên hợp đồng của chính mình và webhook SePay (không có
 *       tenant context) không bị guard này chặn.</li>
 *   <li>Ma trận lỗi phân biệt: chưa gán tenant vs SUSPENDED vs CLOSED dùng
 *       ba message khác nhau (xem các hằng {@code MSG_*}), để FE/log phân
 *       biệt được mà không cần đoán từ mã 403 chung.</li>
 * </ul>
 *
 * <p>Về {@code AgentRepository}: Spring luôn inject bean thật (field này
 * optional chỉ để test unit cũ dùng {@code new PermissionService()} vẫn
 * chạy). Khi repository null (chỉ xảy ra ở test không set repo), các check
 * trạng thái SUSPENDED/CLOSED được bỏ qua — nhưng check fail-closed
 * "chưa gán tenant" (chỉ cần TenantContext) thì vẫn enforce đầy đủ.</p>
 */
@Slf4j
@Service
public class PermissionService {

    /** Agent chưa gán tenant gọi API theo tenant. */
    public static final String MSG_TENANT_UNASSIGNED =
            "Tài khoản đại lý chưa được gán tenant — liên hệ ADMIN để được gán tenant trước khi xem dữ liệu theo tenant.";

    /** Nhân viên tenant SUSPENDED ghi (chỉ đọc mới được). */
    public static final String MSG_TENANT_SUSPENDED_WRITE =
            "Tenant đang tạm ngưng (SUSPENDED) — nhân viên tenant chỉ được đọc, không được ghi.";

    /** Nhân viên tenant CLOSED ghi. */
    public static final String MSG_TENANT_CLOSED_WRITE =
            "Tenant đã đóng (CLOSED) — nhân viên tenant không được ghi; chỉ ADMIN đọc lịch sử.";

    /** Nhân viên tenant CLOSED đọc (chỉ ADMIN đọc lịch sử). */
    public static final String MSG_TENANT_CLOSED_READ =
            "Tenant đã đóng (CLOSED) — nhân viên tenant không được đọc dữ liệu tenant; chỉ ADMIN đọc lịch sử.";

    @Autowired(required = false)
    private AgentRepository agentRepository;

    public PermissionService() {
    }

    /** Constructor cho test muốn gắn AgentRepository mock (status guard). */
    public PermissionService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    /** Setter cho test dùng no-arg constructor nhưng vẫn muốn check trạng thái tenant. */
    public void setAgentRepository(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

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

    /**
     * Kế toán (FINANCE): role nền tảng như ADMIN/UW (đứng ngoài tenant)
     * nhưng chỉ được làm việc với các API tiền. Mọi check tiền dùng helper
     * này + {@link #checkMoneyRead}; KHÔNG đưa FINANCE vào
     * {@link #canReadPolicy} (cửa đó mở cả GET /policies lẫn trang chi
     * tiết hợp đồng — FINANCE không được vào hai cửa đó).
     */
    public boolean hasFinanceRole() {
        return hasRole(Role.ROLE_FINANCE.name());
    }

    public boolean hasCustomerRole() {
        List<String> roles = currentRoles();
        return roles.contains(Role.ROLE_CUSTOMER.name()) || roles.contains(Role.ROLE_USER.name());
    }

    public boolean hasAgentRole() {
        return hasRole(Role.ROLE_AGENT.name());
    }

    /**
     * Tenant của request hiện tại — SaaS Phase 2: {@code null} = caller
     * không thuộc tenant nào HOẶC ADMIN/UW bypass. Không được suy luận
     * bypass chỉ từ tenant null — luôn kết hợp với role trước khi quyết
     * quyền (xem {@link #requireAgentTenant()}).
     */
    public String currentTenantId() {
        return TenantContext.getTenantId();
    }

    /**
     * Policy có thuộc tenant của caller hiện tại không (so sánh Agent.id thuần túy).
     */
    public boolean isTenantMember(Policy policy) {
        String tenantId = currentTenantId();
        return policy != null && tenantId != null
                && tenantId.equals(policy.getAgentId());
    }

    /** Trạng thái của tenant hiện tại, hoặc empty nếu không có tenant / không tìm thấy Agent / không có repo. */
    public Optional<AgentStatus> currentTenantStatus() {
        String tenantId = currentTenantId();
        if (tenantId == null || agentRepository == null) {
            return Optional.empty();
        }
        return agentRepository.findById(tenantId).map(Agent::getStatus);
    }

    /**
     * Fail-closed cho AGENT chưa gán tenant (Track B mục 2).
     * ADMIN/UW bypass theo role nên trả về tenant hiện tại (có thể null) mà
     * không throw. Caller không mang ROLE_AGENT cũng không throw ở đây —
     * các API theo tenant tự quyết tiếp (vd. stats throw "chỉ AGENT/ADMIN").
     *
     * @return tenantId của agent (không null khi caller là AGENT)
     * @throws ForbiddenException với message phân biệt khi AGENT chưa có tenant
     */
    public String requireAgentTenant() {
        if (isAdmin() || isUnderwriter() || !hasAgentRole()) {
            return currentTenantId();
        }
        String tenantId = currentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ForbiddenException(MSG_TENANT_UNASSIGNED);
        }
        return tenantId;
    }

    /**
     * Guard đọc cho nhân viên tenant: AGENT phải có tenant (fail-closed) và
     * tenant không được CLOSED (SUSPENDED vẫn đọc được). ADMIN/UW và
     * CUSTOMER thuần không đi qua guard này.
     */
    public void checkTenantReadForStaff() {
        if (isAdmin() || isUnderwriter() || !hasAgentRole()) {
            return;
        }
        requireAgentTenant();
        if (currentTenantStatus().map(s -> s == AgentStatus.CLOSED).orElse(false)) {
            throw new ForbiddenException(MSG_TENANT_CLOSED_READ);
        }
    }

    /**
     * Guard ghi cho nhân viên tenant: AGENT phải có tenant (fail-closed),
     * tenant SUSPENDED chặn mọi ghi, tenant CLOSED chặn mọi ghi.
     * ADMIN/UW bypass; CUSTOMER thuần không đi qua guard này (khách ghi
     * trên hợp đồng của chính mình không phải "ghi của nhân viên").
     */
    public void checkTenantWriteForStaff() {
        if (isAdmin() || isUnderwriter() || !hasAgentRole()) {
            return;
        }
        requireAgentTenant();
        Optional<AgentStatus> status = currentTenantStatus();
        if (status.map(s -> s == AgentStatus.SUSPENDED).orElse(false)) {
            throw new ForbiddenException(MSG_TENANT_SUSPENDED_WRITE);
        }
        if (status.map(s -> s == AgentStatus.CLOSED).orElse(false)) {
            throw new ForbiddenException(MSG_TENANT_CLOSED_WRITE);
        }
    }

    /**
     * Caller có đang hành động với tư cách nhân viên tenant trên policy này
     * không: mang ROLE_AGENT, policy thuộc tenant của caller, và caller
     * KHÔNG phải chính người được bảo hiểm của policy (trường hợp sau là
     * phía CUSTOMER của tài khoản dual-role — không bị guard trạng thái
     * tenant chặn, theo Track B mục 5).
     */
    public boolean isStaffActingOn(Policy policy, String principal) {
        if (policy == null || principal == null || !hasAgentRole()) {
            return false;
        }
        if (!isTenantMember(policy)) {
            return false;
        }
        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        return !emailEquals(insuredEmail, principal);
    }

    /**
     * Guard ghi cho một policy cụ thể: chỉ chặn khi caller đang hành động
     * với tư cách nhân viên tenant trên policy đó (xem
     * {@link #isStaffActingOn}). ADMIN/UW bypass; CUSTOMER (kể cả phía
     * CUSTOMER của dual-role) và webhook (không có SecurityContext) không
     * bị chặn.
     */
    public void checkTenantWriteForPolicy(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null || !isStaffActingOn(policy, principal)) {
            return;
        }
        checkTenantWriteForStaff();
    }

    private static boolean emailEquals(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Phe cầu theo QUAN HỆ với policy — Phase 2: insured (customer) HOẶC
     * membership tenant (agent). Email {@code assignedAgentEmail} cũ KHÔNG
     * còn được dùng để quyết quyền (PLAN §2.4 bước 2); policy legacy chưa
     * migration (agentId null) thì agent không còn thấy qua email cũ nữa
     * cho tới khi ADMIN gán tenant — đó chính là fail-closed của cutover.
     */
    public boolean isDemandSide(Policy policy, String principal) {
        if (policy == null || principal == null) {
            return false;
        }
        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        return emailEquals(insuredEmail, principal) || isTenantMember(policy);
    }

    public boolean canReadPolicy(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (isAdmin() || isUnderwriter()) {
            return true;
        }
        // Phía CUSTOMER (kể cả dual-role): hợp đồng của chính mình đọc được
        // bất kể trạng thái tenant — khách không bị khóa oan vì đại lý đóng.
        String insuredEmail = policy != null && policy.getInsured() != null
                ? policy.getInsured().getEmail() : null;
        if (emailEquals(insuredEmail, principal)) {
            return true;
        }
        // Phía nhân viên tenant: phải thuộc tenant và tenant không CLOSED.
        if (!isTenantMember(policy)) {
            return false;
        }
        return !currentTenantStatus().map(s -> s == AgentStatus.CLOSED).orElse(false);
    }

    public void checkPolicyRead(Policy policy) {
        if (!canReadPolicy(policy)) {
            throw new ForbiddenException("Bạn không có quyền xem hợp đồng này.");
        }
    }

    /**
     * Đọc ngữ cảnh tiền của một policy — CHỈ dùng cho các endpoint tiền
     * (tra cứu trạng thái payment, lịch sử payment, ledger, invoice):
     * ADMIN/UW đọc mọi policy như cũ; FINANCE cũng đọc mọi policy nhưng
     * CHỈ qua các endpoint tiền này (không mở GET /policies hay trang chi
     * tiết hợp đồng — hai cửa đó vẫn đi {@link #checkPolicyRead}); bên cầu
     * giữ quyền đọc cũ. FINANCE không bao giờ là bên cầu nên không lọt
     * qua nhánh cuối.
     */
    public boolean canReadMoneyContext(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (isAdmin() || isUnderwriter() || hasFinanceRole()) {
            return true;
        }
        return canReadPolicy(policy);
    }

    public void checkMoneyRead(Policy policy) {
        if (!canReadMoneyContext(policy)) {
            throw new ForbiddenException("Bạn không có quyền xem thông tin tiền của hợp đồng này.");
        }
    }

    /**
     * Ghi (sửa/xóa/location/coverage/endorsement/payment-claim): ADMIN mọi
     * policy; CUSTOMER/AGENT trong phạm vi. UW không ghi. Nhân viên tenant
     * trên policy của tenant mình còn phải qua guard trạng thái
     * (SUSPENDED/CLOSED chặn ghi); phía CUSTOMER (insured == principal)
     * không bị guard trạng thái chặn.
     */
    public void checkPolicyWrite(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền sửa hợp đồng này.");
        }
        if (isAdmin() || isDemandSide(policy, principal)) {
            if (!isAdmin()) {
                checkTenantWriteForPolicy(policy);
            }
            return;
        }
        throw new ForbiddenException("Bạn không có quyền sửa hợp đồng này.");
    }

    /** Đọc negotiation: phe cầu trong phạm vi (kèm guard đọc CLOSED), UNDERWRITER, ADMIN (xem). */
    public void checkNegotiationRead(Policy policy) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem thương lượng của hợp đồng này.");
        }
        if (isAdmin() || isUnderwriter() || canReadPolicy(policy)) {
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
     * Bảng chuyển trạng thái → role, enforce ở service.
     * BOUND → ACTIVE: chỉ ADMIN (kích hoạt trực tiếp không qua thanh toán).
     * Xác nhận tay của FINANCE đã bỏ — tiền về qua webhook SePay là kích
     * hoạt thẳng sang ACTIVE, không còn bước đối soát tay. Nhân viên tenant
     * đổi trạng thái policy của tenant mình còn phải qua guard trạng thái
     * (SUSPENDED/CLOSED chặn); phía CUSTOMER không bị chặn.
     */
    public void checkTransition(Policy policy, PolicyStatus from, PolicyStatus to) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Vai trò của bạn không được phép chuyển trạng thái từ " + from + " sang " + to + ".");
        }
        if (isAdmin()) {
            return;
        }
        boolean demandAllowed = isDemandSide(policy, principal) && (hasCustomerRole() || hasAgentRole());
        boolean allowed;
        if (from == PolicyStatus.BOUND && to == PolicyStatus.ACTIVE) {
            allowed = false; // chỉ ADMIN (đã return ở trên); FINANCE không còn xác nhận tay
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
        // Đổi trạng thái là ghi: nhân viên tenant bị guard trạng thái chặn
        // (message phân biệt SUSPENDED/CLOSED thắng message chuyển trạng thái chung).
        checkTenantWriteForPolicy(policy);
    }

    /**
     * Ép criteria theo role cho GET /policies (Track B mục 2 + 3):
     * <ul>
     *   <li>CUSTOMER thuần: ép {@code insuredEmail = principal}, xóa mọi
     *       filter tenant client gửi (khách chỉ xem hợp đồng của mình).</li>
     *   <li>AGENT thuần: fail-closed khi chưa gán tenant (403 message phân
     *       biệt, không trả rỗng lặng); tenant CLOSED thì chặn đọc luôn ở
     *       đây; ép {@code agentId = tenant}, xóa filter email cũ
     *       {@code assignedAgentEmail} (scope không còn dùng email).</li>
     *   <li>Dual-role AGENT+CUSTOMER: hợp OR — hợp đồng mình là insured
     *       HOẶC thuộc tenant mình (cờ {@code dualRoleOrScope} để
     *       {@code PolicyServiceImpl} dựng một {@code $or} duy nhất thay
     *       vì AND hai điều kiện). Chưa gán tenant hoặc tenant CLOSED:
     *       phía nhân viên bị lược khỏi OR (chỉ còn phía insured) thay
     *       vì throw, để phía CUSTOMER của tài khoản vẫn xem được hợp
     *       đồng của chính mình.</li>
     *   <li>ADMIN/UNDERWRITER: giữ nguyên (bypass).</li>
     * </ul>
     * Cờ {@code dualRoleOrScope} luôn được method này set lại tường minh
     * nên client không thể tự bật nó qua query param để lách scope.
     */
    public void applySearchScope(PolicySearchCriteria criteria) {
        String principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem danh sách hợp đồng.");
        }
        if (criteria == null) {
            return;
        }
        if (isAdmin() || isUnderwriter()) {
            criteria.setDualRoleOrScope(false);
            return;
        }
        String email = principal.trim().toLowerCase();
        boolean customer = hasCustomerRole();
        boolean agent = hasAgentRole();
        if (agent && customer) {
            // Dual-role: phía CUSTOMER luôn giữ được (hợp đồng của chính
            // mình); phía AGENT fail-closed: chưa gán tenant hoặc tenant
            // CLOSED thì lược phía tenant khỏi OR thay vì throw, để phía
            // CUSTOMER không bị chặn oan (xem javadoc của method).
            String tenantId = currentTenantId();
            criteria.setInsuredEmail(email);
            criteria.setAssignedAgentEmail(null);
            boolean tenantClosed = tenantId != null && currentTenantStatus()
                    .map(s -> s == AgentStatus.CLOSED).orElse(false);
            if (tenantId == null || tenantClosed) {
                // Chỉ còn phía CUSTOMER; phía nhân viên của tenant đã đóng
                // không được đọc (theo §2.1) nhưng không throw để khỏi chặn
                // luôn quyền xem hợp đồng của chính mình.
                criteria.setAgentId(null);
                criteria.setDualRoleOrScope(false);
            } else {
                criteria.setAgentId(tenantId);
                criteria.setDualRoleOrScope(true);
            }
        } else if (agent) {
            checkTenantReadForStaff();
            criteria.setAgentId(requireAgentTenant());
            criteria.setAssignedAgentEmail(null);
            criteria.setDualRoleOrScope(false);
        } else if (customer) {
            criteria.setInsuredEmail(email);
            criteria.setAgentId(null);
            criteria.setAssignedAgentEmail(null);
            criteria.setDualRoleOrScope(false);
        } else {
            criteria.setInsuredEmail(email);
            criteria.setAgentId(null);
            criteria.setAssignedAgentEmail(null);
            criteria.setDualRoleOrScope(false);
        }
    }
}
