package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.model.PendingEndorsementStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.EndorsementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Track 2 — phần nguyên tử của maker-checker, tách riêng khỏi
 * {@link PendingEndorsementServiceImpl} vì hai lý do proxy:
 * <ol>
 *   <li>CAS claim PHẢI chạy ngoài transaction (commit ngay, nhìn thấy ngay
 *       bởi checker khác). {@code Propagation.NOT_SUPPORTED} suspend
 *       transaction hiện tại nếu có (MongoTransactionManager hỗ trợ
 *       suspend — đã kiểm chứng bằng bytecode) và chạy findAndModify không
 *       nằm trong transaction nào. Nếu claim nằm trong transaction của
 *       approve, checker thứ hai sẽ không thấy APPROVING cho tới khi
 *       transaction đầu commit — đúng lúc đã apply xong — nên race không
 *       được chặn.</li>
 *   <li>Apply + đánh dấu APPROVED PHẢI là một transaction duy nhất, và
 *       method đó không thể nằm cùng bean với orchestrator approve() vì
 *       self-invocation không qua proxy (xem PendingEndorsementServiceImpl).</li>
 * </ol>
 *
 * <p>Luồng approve đầy đủ (xem PendingEndorsementServiceImpl.approve):
 * claim (CAS, ngoài tx) → applyAndMarkApproved (trong tx: apply endorsement
 * + pending→APPROVED) → nếu tx fail, orchestrator releaseClaim về PENDING
 * để checker duyệt lại được. Transaction đơn thuần KHÔNG đủ cho race 2
 * checker: cả hai transaction đều đọc PENDING rồi cùng apply; chỉ CAS
 * findAndModify (nguyên tử ở mức single-document, kể cả ngoài transaction)
 * mới cho đúng một người thắng, người thua nhận BadRequest rõ ràng và
 * không bao giờ apply.
 *
 * <p>Reject không cần trạng thái lease: nó là một lần findAndModify
 * PENDING→REJECTED duy nhất, nguyên tử sẵn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingEndorsementApproval {

    private final MongoTemplate mongoTemplate;
    private final PendingEndorsementRepository pendingEndorsementRepository;
    private final PolicyRepository policyRepository;
    private final EndorsementService endorsementService;

    /**
     * CAS claim: chuyển PENDING→APPROVING nguyên tử. Trả về bản ghi đã
     * claim; null (không còn PENDING — đã APPROVED/REJECTED/APPROVING hoặc
     * không tồn tại) → BadRequest "đã được xử lý". Orchestrator đã kiểm
     * tra tồn tại trước nên null ở đây nghĩa là đã có người xử lý trước.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PendingEndorsement claimForApproval(String pendingId) {
        Query query = new Query(Criteria.where("id").is(pendingId)
                .and("status").is(PendingEndorsementStatus.PENDING));
        Update update = new Update().set("status", PendingEndorsementStatus.APPROVING);
        PendingEndorsement claimed = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), PendingEndorsement.class);
        if (claimed == null) {
            throw new BadRequestException("Pending endorsement này đã được xử lý (đã có checker khác nhận duyệt), không thể xử lý lần 2.");
        }
        return claimed;
    }

    /**
     * Trả lease về PENDING khi transaction apply fail (gọi ngoài tx, sau
     * khi tx đã rollback). Best-effort: nếu không còn APPROVING (trường
     * hợp cực hiếm: có người can thiệp tay giữa chừng) thì chỉ log warn —
     * bản ghi kẹt APPROVING cần operator reset (xem runbook), không được
     * ném lỗi che mất lỗi gốc của apply.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void releaseClaim(String pendingId) {
        Query query = new Query(Criteria.where("id").is(pendingId)
                .and("status").is(PendingEndorsementStatus.APPROVING));
        Update update = new Update()
                .set("status", PendingEndorsementStatus.PENDING)
                .set("approvedBy", null)
                .set("approvedAt", null);
        PendingEndorsement released = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), PendingEndorsement.class);
        if (released == null) {
            log.warn("releaseClaim: pending {} no longer APPROVING (may need operator reset if stuck), nothing released", pendingId);
        }
    }

    /**
     * Reject nguyên tử: một lần findAndModify PENDING→REJECTED. Null →
     * đã có người xử lý trước (approve-claim thắng, reject trước, hoặc
     * approve đã xong) → BadRequest rõ ràng, không ghi đè.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PendingEndorsement rejectIfStillPending(String pendingId, String actor) {
        Query query = new Query(Criteria.where("id").is(pendingId)
                .and("status").is(PendingEndorsementStatus.PENDING));
        Update update = new Update()
                .set("status", PendingEndorsementStatus.REJECTED)
                .set("rejectedBy", actor)
                .set("rejectedAt", Instant.now());
        PendingEndorsement rejected = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), PendingEndorsement.class);
        if (rejected == null) {
            throw new BadRequestException("Pending endorsement này đã được xử lý (đã có checker khác xử lý trước), không thể xử lý lần 2.");
        }
        return rejected;
    }

    /**
     * Transaction duy nhất: apply endorsement (bản thân nó cũng
     * {@code @Transactional}, join vào transaction này) + pending
     * APPROVING→APPROVED. Bất kỳ bước nào fail → rollback toàn bộ:
     * policy + snapshot + pending đều về trước-apply (pending ở lại
     * APPROVING vì claim đã commit ngoài tx — orchestrator sẽ release về
     * PENDING). Không nuốt exception.
     */
    @Transactional
    public PendingEndorsement applyAndMarkApproved(PendingEndorsement claimed, String actor) {
        Policy policy = policyRepository.findByPolicyNumber(claimed.getPolicyNumber())
                .or(() -> policyRepository.findById(claimed.getPolicyNumber()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + claimed.getPolicyNumber()));
        endorsementService.applyEndorsementBypassingThreshold(
                policy.getPolicyNumber(), claimed.getRequest(), actor);
        claimed.setStatus(PendingEndorsementStatus.APPROVED);
        claimed.setApprovedBy(actor);
        claimed.setApprovedAt(Instant.now());
        PendingEndorsement saved = pendingEndorsementRepository.save(claimed);
        log.info("Pending endorsement {} approved by {}", claimed.getId(), saved.getApprovedBy());
        return saved;
    }
}
