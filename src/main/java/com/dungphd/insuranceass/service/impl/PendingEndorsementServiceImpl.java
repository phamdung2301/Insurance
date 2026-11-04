package com.dungphd.insuranceass.service.impl;

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
    /**
     * Track 2 — bean nội bộ giữ CAS claim + transaction apply (xem
     * PendingEndorsementApproval). Không thể gộp vào bean này: approve()
     * gọi method @Transactional của chính mình sẽ là self-invocation, không
     * qua proxy → không có transaction; và claim phải chạy ngoài tx.
     */
    private final PendingEndorsementApproval approval;

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

    /**
     * Track 2 — approve qua CAS + transaction (THAY THẾ đường load →
     * requireStillPending → apply → save cũ, đường cũ có 2 lỗ: apply xong
     * mà pending-save fail thì duyệt lại apply trùng, và 2 checker cùng
     * load PENDING thì cùng apply):
     * <ol>
     *   <li>Kiểm tra tồn tại trước (chỉ để phân biệt 404 với "đã xử lý":
     *       read này không quyết định đúng/sai, CAS phía sau mới quyết).</li>
     *   <li>CAS claim PENDING→APPROVING (nguyên tử, ngoài transaction):
     *       chỉ một checker thắng; người thua nhận BadRequest rõ ràng và
     *       không bao giờ apply.</li>
     *   <li>Apply + pending→APPROVED trong MỘT transaction (bean khác, qua
     *       proxy): fail ở bước nào cũng rollback toàn bộ.</li>
     *   <li>Transaction fail → release claim về PENDING (ngoài tx, sau
     *       rollback) để checker duyệt lại được; release fail cũng không
     *       được che lỗi gốc (chỉ log warn).</li>
     * </ol>
     * Method này KHÔNG gắn {@code @Transactional}: nó là orchestrator —
     * claim và release bắt buộc chạy ngoài transaction, còn transaction
     * thật nằm ở {@code PendingEndorsementApproval.applyAndMarkApproved}.
     * Apply đúng luồng applyEndorsement hiện có, BYPASS ngưỡng (không rơi
     * lại vào pending). Actor ghi ledger/snapshot = người duyệt.
     */
    @Override
    public PendingEndorsement approve(String pendingId, String actor) {
        checkCheckerRole();
        loadPendingOrThrow(pendingId);
        PendingEndorsement claimed = approval.claimForApproval(pendingId);
        try {
            PendingEndorsement saved = approval.applyAndMarkApproved(claimed, resolveActor(actor));
            log.info("Pending endorsement {} approved by {}", pendingId, saved.getApprovedBy());
            return saved;
        } catch (RuntimeException applyFailure) {
            try {
                approval.releaseClaim(pendingId);
            } catch (RuntimeException releaseFailure) {
                log.warn("Pending endorsement {} claim release failed after apply failure (may need operator reset): {}",
                        pendingId, releaseFailure.getMessage());
            }
            throw applyFailure;
        }
    }

    /**
     * Track 2 — reject nguyên tử bằng một lần findAndModify PENDING→REJECTED
     * (xem PendingEndorsementApproval): không còn đường load → check →
     * save nên không thể ghi đè kết quả approve của checker khác (approve
     * thắng claim trước thì reject nhận BadRequest, và ngược lại). Policy
     * KHÔNG đổi: chỉ đánh dấu REJECTED, không gọi apply.
     */
    @Override
    public PendingEndorsement reject(String pendingId, String actor) {
        checkCheckerRole();
        loadPendingOrThrow(pendingId);
        PendingEndorsement saved = approval.rejectIfStillPending(pendingId, resolveActor(actor));
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

    private Policy findPolicy(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElse(null);
    }

    private String resolveActor(String actor) {
        return (actor != null && !actor.isBlank()) ? actor.trim() : "System";
    }
}
