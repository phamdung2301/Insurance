package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ApproveClaimRequest;
import com.dungphd.insuranceass.dto.request.RejectClaimRequest;
import com.dungphd.insuranceass.model.Claim;
import com.dungphd.insuranceass.model.ClaimStatus;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.ClaimRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.ClaimServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ClaimNotificationTriggerTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ClaimServiceImpl claimService;

    private Claim underReviewClaim;
    private Policy activePolicy;

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.isAdmin()).thenReturn(true);
        lenient().when(permissionService.currentPrincipal()).thenReturn("admin@example.com");
        activePolicy = Policy.builder().id("pol-1").policyNumber("POL-1001").status(PolicyStatus.ACTIVE)
                .insured(Insured.builder().name("Nguyễn Văn A").email("customer@example.com").build())
                .assignedAgentEmail("agent@example.com").totalPremium(5_000_000.0).build();
        underReviewClaim = Claim.builder().id("claim-1").policyNumber("POL-1001")
                .claimantEmail("customer@example.com").status(ClaimStatus.UNDER_REVIEW)
                .claimedAmount(2_000_000.0).statusHistory(new ArrayList<>()).build();
        lenient().when(claimRepository.findById("claim-1")).thenReturn(Optional.of(underReviewClaim));
        lenient().when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));
        // Như EndorsementNotificationTriggerTest: field optional không được
        // @InjectMocks inject nên set tường minh.
        ReflectionTestUtils.setField(claimService, "notificationService", notificationService);
        ReflectionTestUtils.setField(claimService, "auditLogRepository", auditLogRepository);
    }

    @Test
    void approveClaim_sendsApprovedMailExactlyOnce() {
        var res = claimService.approveClaim("claim-1", ApproveClaimRequest.builder()
                .approvedAmount(1_500_000.0).handlerNote("Đủ hồ sơ").build());
        assertEquals(ClaimStatus.APPROVED, res.getStatus());
        verify(notificationService, times(1)).notifyClaimApproved(any(), eq(activePolicy));
    }

    @Test
    void rejectClaim_sendsNoApprovedMail() {
        var res = claimService.rejectClaim("claim-1", RejectClaimRequest.builder()
                .rejectReason("Thiếu hồ sơ").build());
        assertEquals(ClaimStatus.REJECTED, res.getStatus());
        verify(notificationService, never()).notifyClaimApproved(any(), any());
    }

    @Test
    void reviewClaim_sendsNoApprovedMail() {
        underReviewClaim.setStatus(ClaimStatus.SUBMITTED);
        var res = claimService.reviewClaim("claim-1");
        assertEquals(ClaimStatus.UNDER_REVIEW, res.getStatus());
        verify(notificationService, never()).notifyClaimApproved(any(), any());
    }
}
