package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.TenantPolicyStatsResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.AgentPolicyStatsService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thống kê policy theo tenant (SaaS Phase 2 Track A).
 *
 * <p>Scope theo quy tắc hiện có, không bịa scope mới: ADMIN bypass xem toàn
 * bộ (như phễu cũ); AGENT chỉ xem tenant của mình qua TenantContext
 * (PermissionService.currentTenantId() = Agent.id). AGENT chưa có tenant
 * trong TenantContext → 403 fail-closed (không đoán tenant từ email như
 * phễu cũ, vì scope Phase 2 đếm theo agentId). HTTP layer đã giới hạn
 * /agent/** cho AGENT + ADMIN trong SecurityConfig; service enforce lại
 * fail-closed như mọi service scope khác trong repo. Track B: dùng chung
 * helper PermissionService.requireAgentTenant()/checkTenantReadForStaff()
 * nên tenant CLOSED cũng chặn đọc thống kê (theo §2.1).</p>
 */
@Service
@RequiredArgsConstructor
public class AgentPolicyStatsServiceImpl implements AgentPolicyStatsService {

    private final PolicyRepository policyRepository;
    private final PermissionService permissionService;

    @Override
    public TenantPolicyStatsResponse summaryForCurrentCaller() {
        String principal = permissionService.currentPrincipal();
        if (principal == null || principal.isBlank()) {
            throw new ForbiddenException("Yêu cầu đăng nhập để xem thống kê hợp đồng.");
        }
        if (permissionService.isAdmin()) {
            return TenantPolicyStatsResponse.builder()
                    .tenantId("ALL")
                    .draft(policyRepository.countByStatus(PolicyStatus.DRAFT))
                    .quoted(policyRepository.countByStatus(PolicyStatus.QUOTED))
                    .bound(policyRepository.countByStatus(PolicyStatus.BOUND))
                    .active(policyRepository.countByStatus(PolicyStatus.ACTIVE))
                    .cancelled(policyRepository.countByStatus(PolicyStatus.CANCELLED))
                    .expired(policyRepository.countByStatus(PolicyStatus.EXPIRED))
                    .build();
        }
        if (permissionService.hasAgentRole()) {
            // Track B mục 2: dùng helper fail-closed chung (message phân
            // biệt "chưa gán tenant") + guard đọc CLOSED (nhân viên tenant
            // đã đóng không đọc thống kê tenant; SUSPENDED vẫn đọc được).
            String tenantId = permissionService.requireAgentTenant();
            permissionService.checkTenantReadForStaff();
            return TenantPolicyStatsResponse.builder()
                    .tenantId(tenantId)
                    .draft(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.DRAFT))
                    .quoted(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.QUOTED))
                    .bound(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.BOUND))
                    .active(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.ACTIVE))
                    .cancelled(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.CANCELLED))
                    .expired(policyRepository.countByAgentIdAndStatus(tenantId, PolicyStatus.EXPIRED))
                    .build();
        }
        throw new ForbiddenException("Chỉ đại lý (AGENT) hoặc ADMIN mới được xem thống kê hợp đồng.");
    }
}
