package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.model.PendingEndorsementStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PendingEndorsementApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Track 2 — CAS claim + transaction apply ở tầng mock (annotation
 * {@code @Transactional} vô tác dụng ở đây vì bean không qua Spring proxy;
 * thứ tự gọi + nhánh thua CAS mới là thứ test này chứng minh; race thật
 * và rollback thật thuộc về TransactionRollbackIntegrationTest).
 */
@ExtendWith(MockitoExtension.class)
class PendingEndorsementApprovalTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PendingEndorsementRepository pendingRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private EndorsementService endorsementService;

    private PendingEndorsementApproval approval;

    private PendingEndorsement pending;
    private Policy policy;

    @BeforeEach
    void setUp() {
        approval = new PendingEndorsementApproval(
                mongoTemplate, pendingRepository, policyRepository, endorsementService);
        pending = PendingEndorsement.builder()
                .id("pend-1")
                .policyNumber("POL-MC-01")
                .request(EndorsementRequest.builder().reason("Thay doi").build())
                .status(PendingEndorsementStatus.PENDING)
                .build();
        policy = Policy.builder()
                .id("pol-1").policyNumber("POL-MC-01").status(PolicyStatus.ACTIVE).build();
        lenient().when(policyRepository.findByPolicyNumber("POL-MC-01")).thenReturn(Optional.of(policy));
        lenient().when(pendingRepository.save(any(PendingEndorsement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void claimForApproval_winnerGetsApprovingDoc() {
        PendingEndorsement claimed = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").status(PendingEndorsementStatus.APPROVING).build();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PendingEndorsement.class))).thenReturn(claimed);

        PendingEndorsement result = approval.claimForApproval("pend-1");

        assertEquals(PendingEndorsementStatus.APPROVING, result.getStatus());
    }

    @Test
    void claimForApproval_loserGetsClearErrorAndNeverApplies() {
        // findAndModify trả null = đã có checker khác thắng claim trước.
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PendingEndorsement.class))).thenReturn(null);

        assertThrows(BadRequestException.class, () -> approval.claimForApproval("pend-1"));
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
    }

    @Test
    void rejectIfStillPending_winnerMarksRejected() {
        PendingEndorsement rejected = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").status(PendingEndorsementStatus.REJECTED)
                .rejectedBy("uw@x.com").rejectedAt(Instant.now()).build();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PendingEndorsement.class))).thenReturn(rejected);

        PendingEndorsement result = approval.rejectIfStillPending("pend-1", "uw@x.com");

        assertEquals(PendingEndorsementStatus.REJECTED, result.getStatus());
        assertEquals("uw@x.com", result.getRejectedBy());
    }

    @Test
    void rejectIfStillPending_loserGetsClearError() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PendingEndorsement.class))).thenReturn(null);

        assertThrows(BadRequestException.class, () -> approval.rejectIfStillPending("pend-1", "uw@x.com"));
    }

    @Test
    void applyAndMarkApproved_appliesThenMarksApproved() {
        PendingEndorsement claimed = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").request(pending.getRequest())
                .status(PendingEndorsementStatus.APPROVING).build();

        PendingEndorsement result = approval.applyAndMarkApproved(claimed, "uw@x.com");

        // Thứ tự: apply trước, save APPROVED sau (verify thứ tự bằng
        // InOrder: apply gọi trước khi pending save).
        var inOrder = inOrder(endorsementService, pendingRepository);
        inOrder.verify(endorsementService).applyEndorsementBypassingThreshold(
                eq("POL-MC-01"), eq(pending.getRequest()), eq("uw@x.com"));
        inOrder.verify(pendingRepository).save(any(PendingEndorsement.class));
        assertEquals(PendingEndorsementStatus.APPROVED, result.getStatus());
        assertEquals("uw@x.com", result.getApprovedBy());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    void applyAndMarkApproved_policyMissing_throwsBeforeApply() {
        when(policyRepository.findByPolicyNumber("POL-MISSING")).thenReturn(Optional.empty());
        when(policyRepository.findById("POL-MISSING")).thenReturn(Optional.empty());
        PendingEndorsement claimed = PendingEndorsement.builder()
                .id("pend-9").policyNumber("POL-MISSING").request(pending.getRequest())
                .status(PendingEndorsementStatus.APPROVING).build();

        assertThrows(ResourceNotFoundException.class, () -> approval.applyAndMarkApproved(claimed, "uw@x.com"));
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
        verify(pendingRepository, never()).save(any());
    }

    @Test
    void releaseClaim_neverThrowsEvenWhenNothingToRelease() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PendingEndorsement.class))).thenReturn(null);

        // Chỉ log warn: release không được che lỗi gốc của apply.
        assertDoesNotThrow(() -> approval.releaseClaim("pend-1"));
    }
}
