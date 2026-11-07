package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.model.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicySearchCriteria {

    private PolicyStatus status;

    @jakarta.validation.constraints.Size(max = 100, message = "Tên người được bảo hiểm tối đa 100 ký tự.")
    private String insuredName;

    /** Lọc chính xác theo email người được bảo hiểm (dùng cho /policies/mine) */
    private String insuredEmail;

    /**
     * Lọc theo agent được giao (server ép theo role, PLAN §1.4).
     *
     * @deprecated Phase 1 giữ để tương thích ngược; từ Phase 2 scope chuyển
     * sang {@link #agentId} và field này chỉ còn được server dịch email→agentId
     * cho ADMIN (PLAN §2.4). Không xóa trong Phase 1, không đổi hành vi.
     */
    @Deprecated
    private String assignedAgentEmail;

    /**
     * Lọc theo tenant sở hữu policy (Agent.id). Phase 2: applySearchScope
     * ép field này cho AGENT (thuần và dual-role); ADMIN/UW dùng để xem
     * theo từng agent.
     */
    private String agentId;

    /**
     * Phase 2 Track B — CHỈ server set (trong
     * {@code PermissionService.applySearchScope} cho tài khoản dual-role
     * AGENT+CUSTOMER): khi true, {@code PolicyServiceImpl} dựng MỘT
     * {@code $or} duy nhất ({@code insured.email == principal} HOẶC
     * {@code agentId == tenant}) thay vì AND hai điều kiện riêng lẻ.
     * applySearchScope luôn set lại cờ này tường minh cho mọi role nên
     * client không thể tự bật qua query param để lách scope; các đường
     * search không qua applySearchScope (vd. /mine) cũng set false.
     */
    @Builder.Default
    private boolean dualRoleOrScope = false;

    /** true → chỉ trả policy có paymentClaimedAt != null (hàng đợi FINANCE, PLAN §1.6) */
    private Boolean paymentClaimedOnly;

    @jakarta.validation.constraints.Size(max = 100, message = "Từ khóa tìm kiếm tối đa 100 ký tự.")
    private String keyword;

    @jakarta.validation.constraints.Size(max = 100, message = "Địa điểm tối đa 100 ký tự.")
    private String location;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant effectiveDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant effectiveDateTo;

    @jakarta.validation.constraints.Size(max = 100, message = "Số hợp đồng tối đa 100 ký tự.")
    private String policyNumber;

    @jakarta.validation.constraints.Size(max = 100, message = "Mã quyền lợi tối đa 100 ký tự.")
    private String coverageCode;

    @jakarta.validation.constraints.Size(max = 100, message = "Loại quyền lợi tối đa 100 ký tự.")
    private String coverageType;

    private Double minPremium;

    private Double maxPremium;

    @jakarta.validation.constraints.Min(value = 0, message = "Số trang phải lớn hơn hoặc bằng 0.")
    @Builder.Default
    private int page = 0;

    @jakarta.validation.constraints.Min(value = 1, message = "Kích thước trang tối thiểu là 1.")
    @jakarta.validation.constraints.Max(value = 100, message = "Kích thước trang tối đa là 100.")
    @Builder.Default
    private int size = 10;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "DESC";
}
