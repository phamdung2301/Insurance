package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.model.PendingEndorsementStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.PendingEndorsementService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingEndorsementServiceImpl implements PendingEndorsementService {

    private final PendingEndorsementRepository pendingEndorsementRepository;
    private final PolicyRepository policyRepository;
    private final EndorsementService endorsementService;
    private final PermissionService permissionService;

    @Override
    public List<PendingEndorsement> listPendingForCurrentUser() {
        String principal = permissionService.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem danh sách chờ duyệt.");
        }
        List<PendingEndorsement> allPending =
                pendingEndorsementRepository.findByStatusOrderByRequestedAtAsc(PendingEndorsementStatus.PENDING);
        if (permissionService.isUnderwriter() || permissionService.isAdmin()) {
            return allPending;
        }
        if (permissionService.isFinance()) {
            throw new ForbiddenException("FINANCE không có quyền xem danh sách chờ duyệt endorsement.");
        }
        // CUSTOMER / AGENT / role khác: chỉ pending của policy trong phạm vi
        // của mình (chủ policy hoặc agent được giao), theo quan hệ — không
        // dùng canReadPolicy vì nó cho FINANCE đọc (đã chặn ở trên) và cho
        // UW/ADMIN (đã return ở trên). Policy đã xóa thì loại khỏi danh sách.
        return allPending.stream()
                .filter(pending -> {
                    Policy policy = findPolicy(pending.getPolicyNumber());
                    return policy != null && permissionService.isDemandSide(policy, principal);
                })
                .collect(Collectors.toList());
    }

    @Override
    public PendingEndorsement approve(String pendingId, String actor) {
        checkCheckerRole();
        PendingEndorsement pending = loadPendingOrThrow(pendingId);
        requireStillPending(pending);
        Policy policy = findPolicy(pending.getPolicyNumber());
        if (policy == null) {
            throw new ResourceNotFoundException("Policy not found with number or ID: " + pending.getPolicyNumber());
        }
        // Apply đúng luồng applyEndorsement hiện có, BYPASS ngưỡng (không rơi
        // lại vào pending). Actor ghi ledger/snapshot = người duyệt.
        endorsementService.applyEndorsementBypassingThreshold(
                policy.getPolicyNumber(), pending.getRequest(), resolveActor(actor));
        pending.setStatus(PendingEndorsementStatus.APPROVED);
        pending.setApprovedBy(resolveActor(actor));
        pending.setApprovedAt(Instant.now());
        PendingEndorsement saved = pendingEndorsementRepository.save(pending);
        log.info("Pending endorsement {} approved by {}", pendingId, saved.getApprovedBy());
        return saved;
    }

    @Override
    public PendingEndorsement reject(String pendingId, String actor) {
        checkCheckerRole();
        PendingEndorsement pending = loadPendingOrThrow(pendingId);
        requireStillPending(pending);
        // Policy KHÔNG đổi: chỉ đánh dấu REJECTED, không gọi apply.
        pending.setStatus(PendingEndorsementStatus.REJECTED);
        pending.setRejectedBy(resolveActor(actor));
        pending.setRejectedAt(Instant.now());
        PendingEndorsement saved = pendingEndorsementRepository.save(pending);
        log.info("Pending endorsement {} rejected by {}", pendingId, saved.getRejectedBy());
        return saved;
    }

    /**
     * Checker duy nhất là UNDERWRITER — ADMIN cũng bị chặn, theo đúng vai trò
     * maker (người yêu cầu) / checker (UW thẩm định) của đợt 4B. ADMIN vẫn
     * xem được danh sách (list), nhưng không duyệt/từ chối.
     */
    private void checkCheckerRole() {
        String principal = permissionService.currentPrincipal();
        if (principal == null || !permissionService.isUnderwriter()) {
            throw new ForbiddenException("Chỉ UNDERWRITER mới được duyệt/từ chối endorsement chờ duyệt.");
        }
    }

    private PendingEndorsement loadPendingOrThrow(String pendingId) {
        return pendingEndorsementRepository.findById(pendingId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending endorsement not found: " + pendingId));
    }

    private void requireStillPending(PendingEndorsement pending) {
        if (pending.getStatus() != PendingEndorsementStatus.PENDING) {
            throw new BadRequestException("Pending endorsement này đã được xử lý (trạng thái hiện tại: "
                    + pending.getStatus() + "), không thể xử lý lần 2.");
        }
    }

    private Policy findPolicy(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElse(null);
    }

    private String resolveActor(String actor) {
        return (actor != null && !actor.isBlank()) ? actor.trim() : "System";
    }
}
