package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PendingEndorsementApproval;
import com.dungphd.insuranceass.service.impl.PendingEndorsementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * Đợt 4B — duyệt/từ chối + scope danh sách của PendingEndorsementService.
 * PermissionService được mock hoàn toàn (service này không tự đọc JWT).
 */
@ExtendWith(MockitoExtension.class)
class PendingEndorsementServiceImplTest {

    @Mock
    private PendingEndorsementRepository pendingRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private EndorsementService endorsementService;

    @Mock
    private PermissionService permissionService;

    // Track 2: bean CAS + transaction nội bộ (mock hoàn toàn ở test này;
    // hành vi thật của nó được test ở PendingEndorsementApprovalTest).
    @Mock
    private PendingEndorsementApproval approval;

    @InjectMocks
    private PendingEndorsementServiceImpl pendingService;

    private Policy policy;
    private PendingEndorsement pending;

    @BeforeEach
    void setUp() {
        policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-MC-01")
                .status(PolicyStatus.ACTIVE)
                .version(1)
                .totalPremium(1000.0)
                .build();
        EndorsementRequest req = EndorsementRequest.builder().reason("Thay doi han muc").build();
        pending = PendingEndorsement.builder()
                .id("pend-1")
                .policyNumber("POL-MC-01")
                .request(req)
                .requestedBy("cust@x.com")
                .requestedAt(Instant.now())
                .premiumChangePct(0.5)
                .status(PendingEndorsementStatus.PENDING)
                .build();
        // Lenient: không phải test nào cũng dùng hết stub setUp (vd. test
        // 403 xảy ra trước khi load pending/policy).
        lenient().when(policyRepository.findByPolicyNumber("POL-MC-01")).thenReturn(Optional.of(policy));
        lenient().when(pendingRepository.findById("pend-1")).thenReturn(Optional.of(pending));
        lenient().when(pendingRepository.save(any(PendingEndorsement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void asUnderwriter() {
        lenient().when(permissionService.currentPrincipal()).thenReturn("uw@x.com");
        lenient().when(permissionService.isUnderwriter()).thenReturn(true);
        lenient().when(permissionService.isAdmin()).thenReturn(false);
    }

    @Test
    void approve_ByUnderwriter_AppliesViaBypassAndMarksApproved() {
        asUnderwriter();
        // Track 2: approve đi qua CAS claim + transaction apply (bean
        // approval mock). applyEndorsement thật không còn gọi trực tiếp từ
        // service này mà nằm trong approval.applyAndMarkApproved.
        PendingEndorsement claimed = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").request(pending.getRequest())
                .status(PendingEndorsementStatus.APPROVING).build();
        PendingEndorsement approved = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").request(pending.getRequest())
                .status(PendingEndorsementStatus.APPROVED)
                .approvedBy("uw@x.com").approvedAt(Instant.now()).build();
        when(approval.claimForApproval("pend-1")).thenReturn(claimed);
        when(approval.applyAndMarkApproved(claimed, "uw@x.com")).thenReturn(approved);

        PendingEndorsement result = pendingService.approve("pend-1", "uw@x.com");

        verify(approval).claimForApproval("pend-1");
        verify(approval).applyAndMarkApproved(claimed, "uw@x.com");
        verify(approval, never()).releaseClaim(any());
        assertEquals(PendingEndorsementStatus.APPROVED, result.getStatus());
        assertEquals("uw@x.com", result.getApprovedBy());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    void approve_SecondTime_Blocked() {
        asUnderwriter();
        // Track 2: checker thứ hai thua CAS claim → BadRequest, apply không
        // bao giờ chạy (kể cả khi bản ghi trong bộ nhớ đã APPROVED).
        when(approval.claimForApproval("pend-1"))
                .thenThrow(new BadRequestException("Pending endorsement này đã được xử lý"));

        assertThrows(BadRequestException.class, () -> pendingService.approve("pend-1", "uw@x.com"));
        verify(approval, never()).applyAndMarkApproved(any(), any());
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
    }

    @Test
    void approve_ApplyFails_ReleasesClaimAndRethrows() {
        asUnderwriter();
        PendingEndorsement claimed = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").request(pending.getRequest())
                .status(PendingEndorsementStatus.APPROVING).build();
        when(approval.claimForApproval("pend-1")).thenReturn(claimed);
        when(approval.applyAndMarkApproved(claimed, "uw@x.com"))
                .thenThrow(new RuntimeException("simulated DB failure on apply"));

        assertThrows(RuntimeException.class, () -> pendingService.approve("pend-1", "uw@x.com"));
        // Claim được trả về PENDING để checker duyệt lại được.
        verify(approval).releaseClaim("pend-1");
    }

    @Test
    void approve_ByCustomer_Forbidden() {
        when(permissionService.currentPrincipal()).thenReturn("cust@x.com");
        when(permissionService.isUnderwriter()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> pendingService.approve("pend-1", "cust@x.com"));
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
    }

    @Test
    void approve_ByAdmin_Forbidden_CheckerIsUnderwriterOnly() {
        when(permissionService.currentPrincipal()).thenReturn("admin@x.com");
        when(permissionService.isUnderwriter()).thenReturn(false);
        // Lenient: checkCheckerRole chỉ hỏi isUnderwriter, stub isAdmin chỉ để
        // ghi rõ ngữ cảnh "kể cả ADMIN cũng bị chặn".
        lenient().when(permissionService.isAdmin()).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> pendingService.approve("pend-1", "admin@x.com"));
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
    }

    @Test
    void reject_ByUnderwriter_LeavesPolicyUntouchedAndMarksRejected() {
        asUnderwriter();
        // Track 2: reject đi qua CAS findAndModify (bean approval mock).
        PendingEndorsement rejected = PendingEndorsement.builder()
                .id("pend-1").policyNumber("POL-MC-01").request(pending.getRequest())
                .status(PendingEndorsementStatus.REJECTED)
                .rejectedBy("uw@x.com").rejectedAt(Instant.now()).build();
        when(approval.rejectIfStillPending("pend-1", "uw@x.com")).thenReturn(rejected);

        PendingEndorsement result = pendingService.reject("pend-1", "uw@x.com");

        verify(approval).rejectIfStillPending("pend-1", "uw@x.com");
        verify(endorsementService, never()).applyEndorsementBypassingThreshold(any(), any(), any());
        assertEquals(PendingEndorsementStatus.REJECTED, result.getStatus());
        assertEquals("uw@x.com", result.getRejectedBy());
        assertNotNull(result.getRejectedAt());
    }

    @Test
    void reject_SecondTime_Blocked() {
        asUnderwriter();
        // Track 2: đã có người xử lý trước → CAS trả null → BadRequest.
        when(approval.rejectIfStillPending("pend-1", "uw@x.com"))
                .thenThrow(new BadRequestException("Pending endorsement này đã được xử lý"));

        assertThrows(BadRequestException.class, () -> pendingService.reject("pend-1", "uw@x.com"));
    }

    @Test
    void list_Underwriter_SeesAll() {
        asUnderwriter();
        PendingEndorsement other = PendingEndorsement.builder()
                .id("pend-2").policyNumber("POL-OTHER").status(PendingEndorsementStatus.PENDING).build();
        when(pendingRepository.findByStatusOrderByRequestedAtAsc(PendingEndorsementStatus.PENDING))
                .thenReturn(List.of(pending, other));

        List<PendingEndorsement> result = pendingService.listPendingForCurrentUser();
        assertEquals(2, result.size());
    }

    @Test
    void list_Admin_SeesAll_ReadOnly() {
        when(permissionService.currentPrincipal()).thenReturn("admin@x.com");
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.isAdmin()).thenReturn(true);
        when(pendingRepository.findByStatusOrderByRequestedAtAsc(PendingEndorsementStatus.PENDING))
                .thenReturn(List.of(pending));

        assertEquals(1, pendingService.listPendingForCurrentUser().size());
    }

    @Test
    void list_Customer_SeesOnlyOwnPolicies() {
        when(permissionService.currentPrincipal()).thenReturn("cust@x.com");
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.isAdmin()).thenReturn(false);
        Policy otherPolicy = Policy.builder().policyNumber("POL-OTHER").build();
        when(policyRepository.findByPolicyNumber("POL-OTHER")).thenReturn(Optional.of(otherPolicy));
        when(permissionService.isDemandSide(eq(policy), eq("cust@x.com"))).thenReturn(true);
        when(permissionService.isDemandSide(eq(otherPolicy), eq("cust@x.com"))).thenReturn(false);
        PendingEndorsement other = PendingEndorsement.builder()
                .id("pend-2").policyNumber("POL-OTHER").status(PendingEndorsementStatus.PENDING).build();
        when(pendingRepository.findByStatusOrderByRequestedAtAsc(PendingEndorsementStatus.PENDING))
                .thenReturn(List.of(pending, other));

        List<PendingEndorsement> result = pendingService.listPendingForCurrentUser();
        assertEquals(1, result.size());
        assertEquals("pend-1", result.get(0).getId());
    }

    @Test
    void list_NoPrincipal_Forbidden() {
        when(permissionService.currentPrincipal()).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> pendingService.listPendingForCurrentUser());
    }
}
