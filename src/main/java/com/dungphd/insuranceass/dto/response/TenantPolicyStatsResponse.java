package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response của GET /agent/policies/summary (SaaS Phase 2 Track A): số lượng
 * policy của tenant hiện tại theo từng trạng thái. Đây là phần thống kê
 * tách ra từ phễu cũ — KHÔNG có cột tiền nào.
 *
 * <p>{@code tenantId} là Agent.id của tenant (resolve từ TenantContext qua
 * JWT); "ALL" khi ADMIN xem toàn bộ hệ thống.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPolicyStatsResponse {

    /** Agent.id của tenant; "ALL" khi ADMIN xem toàn bộ. */
    private String tenantId;

    @Builder.Default
    private long draft = 0;

    @Builder.Default
    private long quoted = 0;

    @Builder.Default
    private long bound = 0;

    @Builder.Default
    private long active = 0;

    @Builder.Default
    private long cancelled = 0;

    @Builder.Default
    private long expired = 0;

    public long getTotal() {
        return draft + quoted + bound + active + cancelled + expired;
    }
}
