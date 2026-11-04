package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.AgentFunnelResponse;
import com.dungphd.insuranceass.dto.response.CommissionListResponse;
import com.dungphd.insuranceass.model.Policy;

/**
 * Hoa hồng agent (đợt 3A) — ghi nhận phụ, KHÔNG nối hook trong đợt này.
 * Coordinator nối vào PolicyServiceImpl.transitionStatus ở merge:
 * BOUND → {@link #createForBound(Policy)}, ACTIVE → {@link #markEarned(Policy)}.
 *
 * <p>Cả 2 method hook đều FAILURE-ISOLATED theo kiểu notification: mọi
 * exception bị catch + log bên trong impl, KHÔNG throw ra ngoài — chuyển
 * trạng thái chính không bao giờ fail vì hoa hồng. Policy không có
 * assignedAgentEmail thì bỏ qua im lặng (chỉ log).
 */
public interface CommissionService {

    /**
     * TỶ LỆ HOA HỒNG — USER CHỈ CẦN ĐỔI 1 SỐ NÀY LÀ XONG.
     * 0.05 = 5% trên Policy.totalPremium. Impl dùng đúng hằng này
     * (qua BigDecimal) để tính amount, không hard-code 0.05 ở chỗ khác.
     */
    double COMMISSION_RATE = 0.05;

    /**
     * Tạo record hoa hồng khi policy chuyển sang BOUND.
     * amount = COMMISSION_RATE * totalPremium, làm tròn về đồng nguyên
     * (HALF_UP, scale 0) — khớp convention tiền tệ của codebase: FE hiển thị
     * VND với 0 chữ số thập phân (formatMoney/formatVND), totalPremium là
     * Double tính bằng đồng.
     * IDEMPOTENT: đã có record cho policyNumber thì không tạo trùng.
     * Không agent / policy null / policyNumber null → bỏ qua, không throw.
     */
    void createForBound(Policy policy);

    /**
     * Chuyển record của policy sang EARNED + set earnedAt (khi policy ACTIVE).
     * Chưa có record → không làm gì, không throw. Đã EARNED → giữ nguyên
     * earnedAt cũ (không ghi đè). Policy null / policyNumber null → bỏ qua.
     */
    void markEarned(Policy policy);

    /**
     * Danh sách hoa hồng + tổng tiền, scope tự enforce (fail-closed):
     * không principal → ForbiddenException; ROLE_ADMIN → tất cả;
     * ROLE_AGENT → chỉ của chính principal (lấy từ SecurityContext, không
     * tin email đối số truyền vào nếu khác principal); role khác
     * (CUSTOMER/UNDERWRITER/FINANCE) → ForbiddenException (403).
     */
    CommissionListResponse listForPrincipal(String principalEmail);

    /**
     * Phễu policy theo status cho agent (cùng rule scope với
     * {@link #listForPrincipal}: AGENT → của mình, ADMIN → toàn bộ,
     * role khác / không principal → ForbiddenException).
     */
    AgentFunnelResponse funnelForPrincipal(String principalEmail);
}
