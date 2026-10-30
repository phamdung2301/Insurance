package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.AgentFunnelResponse;
import com.dungphd.insuranceass.dto.response.CommissionListResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.CommissionStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.CommissionRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.CommissionService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Hoa hồng agent (đợt 3A). Xem contract ở {@link CommissionService}.
 * Hai method hook failure-isolated: toàn bộ thân nằm trong try/catch,
 * log warn và nuốt exception — giống pattern NotificationServiceImpl.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRepository commissionRepository;
    private final PolicyRepository policyRepository;
    private final PermissionService permissionService;

    @Override
    public void createForBound(Policy policy) {
        try {
            if (policy == null || policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
                log.warn("createForBound skipped: policy or policyNumber is null");
                return;
            }
            String agentEmail = policy.getAssignedAgentEmail();
            if (agentEmail == null || agentEmail.isBlank()) {
                log.info("createForBound skipped for policy {}: no assigned agent",
                        policy.getPolicyNumber());
                return;
            }
            if (commissionRepository.existsByPolicyNumber(policy.getPolicyNumber())) {
                log.info("createForBound skipped for policy {}: commission already exists (idempotent)",
                        policy.getPolicyNumber());
                return;
            }
            double amount = calculateAmount(policy.getTotalPremium());
            commissionRepository.save(Commission.builder()
                    .policyNumber(policy.getPolicyNumber())
                    .agentEmail(agentEmail.trim())
                    .amount(amount)
                    .status(CommissionStatus.PENDING)
                    .createdAt(Instant.now())
                    .earnedAt(null)
                    .build());
            log.info("Commission created for policy {} (agent {}, amount {})",
                    policy.getPolicyNumber(), agentEmail, amount);
        } catch (Exception e) {
            // Failure-isolated: hoa hồng là ghi nhận phụ, không được làm fail
            // chuyển trạng thái chính (BOUND) của caller.
            log.warn("Failed to create commission for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public void markEarned(Policy policy) {
        try {
            if (policy == null || policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
                log.warn("markEarned skipped: policy or policyNumber is null");
                return;
            }
            Commission commission = commissionRepository
                    .findByPolicyNumber(policy.getPolicyNumber())
                    .orElse(null);
            if (commission == null) {
                // Chưa có record (ví dụ policy BOUND trước khi tính năng ra đời,
                // hoặc không có agent) → không làm gì, không throw.
                log.info("markEarned skipped for policy {}: no commission record",
                        policy.getPolicyNumber());
                return;
            }
            if (commission.getStatus() == CommissionStatus.EARNED) {
                return; // Đã EARNED: giữ nguyên earnedAt cũ, không ghi đè.
            }
            commission.setStatus(CommissionStatus.EARNED);
            commission.setEarnedAt(Instant.now());
            commissionRepository.save(commission);
            log.info("Commission marked EARNED for policy {}", policy.getPolicyNumber());
        } catch (Exception e) {
            // Failure-isolated: không được làm fail chuyển trạng thái ACTIVE.
            log.warn("Failed to mark commission EARNED for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public CommissionListResponse listForPrincipal(String principalEmail) {
        String scope = resolveScope(principalEmail);
        List<Commission> items;
        if ("ALL".equals(scope)) {
            items = commissionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            items = commissionRepository.findByAgentEmailOrderByCreatedAtDesc(scope);
        }
        double totalPending = 0.0;
        double totalEarned = 0.0;
        for (Commission c : items) {
            double amount = c.getAmount() == null ? 0.0 : c.getAmount();
            if (c.getStatus() == CommissionStatus.EARNED) {
                totalEarned += amount;
            } else {
                totalPending += amount;
            }
        }
        return CommissionListResponse.builder()
                .items(items)
                .totalPending(totalPending)
                .totalEarned(totalEarned)
                .build();
    }

    @Override
    public AgentFunnelResponse funnelForPrincipal(String principalEmail) {
        String scope = resolveScope(principalEmail);
        AgentFunnelResponse.AgentFunnelResponseBuilder builder = AgentFunnelResponse.builder()
                .agentEmail("ALL".equals(scope) ? "ALL" : scope);
        if ("ALL".equals(scope)) {
            builder.draft(policyRepository.countByStatus(PolicyStatus.DRAFT))
                    .quoted(policyRepository.countByStatus(PolicyStatus.QUOTED))
                    .bound(policyRepository.countByStatus(PolicyStatus.BOUND))
                    .active(policyRepository.countByStatus(PolicyStatus.ACTIVE));
        } else {
            builder.draft(policyRepository.countByAssignedAgentEmailAndStatus(scope, PolicyStatus.DRAFT))
                    .quoted(policyRepository.countByAssignedAgentEmailAndStatus(scope, PolicyStatus.QUOTED))
                    .bound(policyRepository.countByAssignedAgentEmailAndStatus(scope, PolicyStatus.BOUND))
                    .active(policyRepository.countByAssignedAgentEmailAndStatus(scope, PolicyStatus.ACTIVE));
        }
        return builder.build();
    }

    /**
     * Scope tự enforce, fail-closed (mẫu UnderwriterQueueServiceImpl):
     * principal lấy từ SecurityContext (PermissionService) mới là nguồn thật;
     * email đối số chỉ để controller truyền qua, nếu khác principal thì
     * principal thật thắng — agent không thể xem của agent khác bằng cách
     * nhờ controller truyền email khác. Không principal → 403.
     * Trả về "ALL" cho ADMIN, email principal cho AGENT, còn lại throw.
     */
    private String resolveScope(String principalEmail) {
        String principal = permissionService.currentPrincipal();
        if (principal == null || principal.isBlank()) {
            throw new ForbiddenException("Yêu cầu đăng nhập để xem hoa hồng.");
        }
        if (principalEmail != null && !principalEmail.isBlank()
                && !principalEmail.trim().equalsIgnoreCase(principal.trim())) {
            log.warn("Commission scope: argument email differs from SecurityContext principal; principal wins");
        }
        if (permissionService.isAdmin()) {
            return "ALL";
        }
        if (permissionService.hasAgentRole()) {
            return principal.trim();
        }
        throw new ForbiddenException("Chỉ đại lý (AGENT) hoặc ADMIN mới được xem hoa hồng.");
    }

    /**
     * amount = COMMISSION_RATE * totalPremium, làm tròn về đồng nguyên
     * (HALF_UP, scale 0) bằng BigDecimal — tránh lỗi float của double
     * (VD 0.05 * 333333 = 16666.650000... phải ra 16667, không phải 16666).
     * totalPremium null → 0.
     */
    static double calculateAmount(Double totalPremium) {
        if (totalPremium == null) {
            return 0.0;
        }
        return BigDecimal.valueOf(totalPremium)
                .multiply(BigDecimal.valueOf(COMMISSION_RATE))
                .setScale(0, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
