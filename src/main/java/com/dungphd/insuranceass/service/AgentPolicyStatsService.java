package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.TenantPolicyStatsResponse;

/**
 * Thống kê policy theo tenant (SaaS Phase 2 Track A) — phần thống kê tách
 * ra từ phễu cũ sau khi bỏ hẳn hoa hồng. Không có cột tiền nào, không
 * đọc collection hoa hồng cũ (đã đóng băng).
 */
public interface AgentPolicyStatsService {

    /**
     * Đếm policy của tenant hiện tại theo trạng thái.
     * ADMIN → toàn bộ hệ thống (tenantId "ALL"); AGENT → tenant của mình
     * (Agent.id từ TenantContext); role khác hoặc AGENT chưa gán tenant
     * → ForbiddenException (fail-closed).
     */
    TenantPolicyStatsResponse summaryForCurrentCaller();
}
