package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.TenantPolicyStatsResponse;
import com.dungphd.insuranceass.service.AgentPolicyStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thống kê policy theo tenant (SaaS Phase 2 Track A) — thay thế phễu cũ
 * của controller hoa hồng đã xóa. Không có cột tiền nào.
 *
 * <p>Không cần matcher riêng trong SecurityConfig: /agent/** đã giới hạn
 * AGENT + ADMIN (matcher hiện có). Scope theo tenant được enforce lại
 * trong AgentPolicyStatsService (ADMIN xem toàn bộ, AGENT xem tenant mình,
 * AGENT chưa gán tenant → 403 fail-closed). Controller không nhận tenant
 * từ client — tenant lấy từ JWT qua TenantContext.</p>
 *
 * <p>Chưa có UI trong track này: UI thống kê là việc sau (xem báo cáo
 * Track A).</p>
 */
@RestController
@RequestMapping({"/agent/policies/summary", "/api/agent/policies/summary"})
@RequiredArgsConstructor
public class AgentPolicyStatsController {

    private final AgentPolicyStatsService agentPolicyStatsService;

    /** GET /agent/policies/summary → {tenantId, draft, quoted, bound, active, cancelled, expired, total}. */
    @GetMapping
    public ApiResponse<TenantPolicyStatsResponse> summary() {
        return ApiResponse.success(
                agentPolicyStatsService.summaryForCurrentCaller(),
                "Thống kê hợp đồng theo tenant.");
    }
}
