package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ApproveClaimRequest;
import com.dungphd.insuranceass.dto.request.CreateClaimRequest;
import com.dungphd.insuranceass.dto.request.RejectClaimRequest;
import com.dungphd.insuranceass.dto.response.ClaimResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.ClaimRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.ClaimServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceImplTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @InjectMocks
    private ClaimServiceImpl claimService;

    private Policy activePolicy;
    private Policy quotedPolicy;
    private Claim sampleClaim;

    @BeforeEach
    void setUp() {
        activePolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-1001")
                .status(PolicyStatus.ACTIVE)
                .insured(Insured.builder().name("Nguyễn Văn A").email("customer@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .totalPremium(5000000.0)
                .build();

        quotedPolicy = Policy.builder()
                .id("pol-2")
                .policyNumber("POL-1002")
                .status(PolicyStatus.QUOTED)
                .insured(Insured.builder().name("Nguyễn Văn A").email("customer@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .totalPremium(5000000.0)
                .build();

        sampleClaim = Claim.builder()
                .id("claim-1")
                .policyNumber("POL-1001")
                .claimantEmail("customer@example.com")
                .status(ClaimStatus.SUBMITTED)
                .claimedAmount(2000000.0)
                .damageDescription("Va chạm xe móp cửa trước")
                .photos(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .createdAt(Instant.now())
                .build();
    }

    // 1. Tạo claim trên policy ACTIVE của mình -> Thành công
    @Test
    void createClaim_byCustomer_activePolicy_success() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1500000.0)
                .damageDescription("Tai nạn làm vỡ kính chiếu hậu")
                .build();

        MockMultipartFile photo = new MockMultipartFile("photos", "glass.jpg", "image/jpeg", "image-content".getBytes());

        ClaimResponse resp = claimService.createClaim(req, List.of(photo));

        assertNotNull(resp);
        assertEquals(ClaimStatus.SUBMITTED, resp.getStatus());
        assertEquals("POL-1001", resp.getPolicyNumber());
        assertEquals(1, resp.getPhotos().size());
        assertEquals("glass.jpg", resp.getPhotos().get(0).getFileName());
        verify(claimRepository).save(any(Claim.class));
    }

    // 2. Tạo claim trên policy không ACTIVE (vd QUOTED, DRAFT) -> 403 Forbidden
    @Test
    void createClaim_policyNotActive_throws403() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1002")).thenReturn(Optional.of(quotedPolicy));

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1002")
                .claimedAmount(1000000.0)
                .damageDescription("Hư hỏng")
                .build();

        assertThrows(ForbiddenException.class, () -> claimService.createClaim(req, null));
    }

    // 3. Tạo claim trên policy của người khác -> 403 Forbidden
    @Test
    void createClaim_policyNotOwned_throws403() {
        when(permissionService.currentPrincipal()).thenReturn("other_customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1000000.0)
                .damageDescription("Hư hỏng")
                .build();

        assertThrows(ForbiddenException.class, () -> claimService.createClaim(req, null));
    }

    // 4. Upload hơn 10 ảnh -> 400 Bad Request
    @Test
    void createClaim_moreThan10Photos_throws400() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(new MockMultipartFile("photos", "photo" + i + ".png", "image/png", "data".getBytes()));
        }

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1000000.0)
                .damageDescription("Mô tả")
                .build();

        assertThrows(BadRequestException.class, () -> claimService.createClaim(req, files));
    }

    // 5. 1 ảnh vượt quá 10MB -> 400 Bad Request
    @Test
    void createClaim_singlePhotoExceeds10MB_throws400() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(11L * 1024 * 1024);
        when(oversized.getOriginalFilename()).thenReturn("big.jpg");

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1000000.0)
                .damageDescription("Mô tả")
                .build();

        assertThrows(BadRequestException.class, () -> claimService.createClaim(req, List.of(oversized)));
    }

    // 6. Tổng kích thước ảnh vượt quá 50MB -> 400 Bad Request
    @Test
    void createClaim_totalSizeExceeds50MB_throws400() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MultipartFile f = mock(MultipartFile.class);
            when(f.getSize()).thenReturn(9L * 1024 * 1024); // 6 * 9MB = 54MB > 50MB
            when(f.getOriginalFilename()).thenReturn("photo" + i + ".jpg");
            files.add(f);
        }

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1000000.0)
                .damageDescription("Mô tả")
                .build();

        assertThrows(BadRequestException.class, () -> claimService.createClaim(req, files));
    }

    // 7. Định dạng file không hợp lệ (không phải jpg, jpeg, png, webp) -> 400 Bad Request
    @Test
    void createClaim_invalidExtension_throws400() {
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        MockMultipartFile file = new MockMultipartFile("photos", "contract.pdf", "application/pdf", "pdf-data".getBytes());

        CreateClaimRequest req = CreateClaimRequest.builder()
                .policyNumber("POL-1001")
                .claimedAmount(1000000.0)
                .damageDescription("Mô tả")
                .build();

        assertThrows(BadRequestException.class, () -> claimService.createClaim(req, List.of(file)));
    }

    // 8. Thêm ảnh: tổng cộng vượt 10 ảnh -> 400 Bad Request
    @Test
    void addPhotos_exceedsMaxPhotos_throws400() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");

        for (int i = 0; i < 9; i++) {
            sampleClaim.getPhotos().add(ClaimPhoto.builder().id("p" + i).fileName("p" + i + ".jpg").build());
        }

        List<MultipartFile> newFiles = List.of(
                new MockMultipartFile("photos", "extra1.jpg", "image/jpeg", "data".getBytes()),
                new MockMultipartFile("photos", "extra2.jpg", "image/jpeg", "data".getBytes())
        );

        assertThrows(BadRequestException.class, () -> claimService.addPhotos("claim-1", newFiles));
    }

    // 9. CUSTOMER xem claim của mình -> thành công
    @Test
    void getClaimById_customerOwnClaim_success() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("customer@example.com");

        ClaimResponse resp = claimService.getClaimById("claim-1");
        assertNotNull(resp);
        assertEquals("claim-1", resp.getId());
    }

    // 10. CUSTOMER xem claim của người khác -> 403 Forbidden
    @Test
    void getClaimById_customerOtherClaim_throws403() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("other@example.com");

        assertThrows(ForbiddenException.class, () -> claimService.getClaimById("claim-1"));
    }

    // 11. AGENT xem claim của khách được giao -> thành công
    @Test
    void getClaimById_agentAssigned_success() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("agent@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        ClaimResponse resp = claimService.getClaimById("claim-1");
        assertNotNull(resp);
        assertEquals("claim-1", resp.getId());
    }

    // 12. AGENT xem claim không được giao -> 403 Forbidden
    @Test
    void getClaimById_agentNotAssigned_throws403() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("other_agent@example.com");
        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(activePolicy));

        assertThrows(ForbiddenException.class, () -> claimService.getClaimById("claim-1"));
    }

    // 13a. UNDERWRITER xem claim bất kỳ -> thành công
    @Test
    void getClaimById_underwriter_success() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("uw@insurance.vn");
        when(permissionService.isUnderwriter()).thenReturn(true);

        ClaimResponse resp = claimService.getClaimById("claim-1");
        assertNotNull(resp);
        assertEquals("claim-1", resp.getId());
    }

    // 13b. ADMIN xem claim bất kỳ -> thành công
    @Test
    void getClaimById_admin_success() {
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("admin@insurance.vn");
        when(permissionService.isAdmin()).thenReturn(true);

        ClaimResponse resp = claimService.getClaimById("claim-1");
        assertNotNull(resp);
        assertEquals("claim-1", resp.getId());
    }

    // 14. Xem ảnh: từ chối khi không có quyền
    @Test
    void getClaimPhoto_forbiddenForUnauthorizedUser() {
        ClaimPhoto photo = ClaimPhoto.builder()
                .id("photo-1")
                .fileName("photo.jpg")
                .filePath("photo.jpg")
                .contentType("image/jpeg")
                .build();
        sampleClaim.getPhotos().add(photo);

        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(permissionService.currentPrincipal()).thenReturn("hacker@example.com");

        assertThrows(ForbiddenException.class, () -> claimService.getClaimPhoto("claim-1", "photo-1"));
    }

    // 15. Luồng chuyển trạng thái chuẩn: SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID
    @Test
    void fullSuccessWorkflow_submitted_to_paid() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(permissionService.currentPrincipal()).thenReturn("admin@insurance.vn");
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArgument(0));

        // 1. SUBMITTED -> UNDER_REVIEW
        ClaimResponse revResp = claimService.reviewClaim("claim-1");
        assertEquals(ClaimStatus.UNDER_REVIEW, revResp.getStatus());
        assertEquals(1, sampleClaim.getStatusHistory().size());

        // 2. UNDER_REVIEW -> APPROVED
        ApproveClaimRequest appReq = ApproveClaimRequest.builder()
                .approvedAmount(1800000.0)
                .handlerNote("Đã thẩm định và trừ khấu trừ 200k")
                .build();
        ClaimResponse appResp = claimService.approveClaim("claim-1", appReq);
        assertEquals(ClaimStatus.APPROVED, appResp.getStatus());
        assertEquals(1800000.0, appResp.getApprovedAmount());
        assertEquals(2, sampleClaim.getStatusHistory().size());

        // 3. APPROVED -> PAID
        ClaimResponse paidResp = claimService.payClaim("claim-1");
        assertEquals(ClaimStatus.PAID, paidResp.getStatus());
        assertEquals(3, sampleClaim.getStatusHistory().size());
    }

    // 16. Luồng từ chối: SUBMITTED -> UNDER_REVIEW -> REJECTED
    @Test
    void fullRejectWorkflow_submitted_to_rejected() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(permissionService.currentPrincipal()).thenReturn("admin@insurance.vn");
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArgument(0));

        // Chuyển sang UNDER_REVIEW
        claimService.reviewClaim("claim-1");

        // Từ chối có lý do
        RejectClaimRequest rejReq = RejectClaimRequest.builder()
                .rejectReason("Tổn thất phát sinh ngoài phạm vi bảo hiểm (điều khoản 4.2)")
                .build();
        ClaimResponse rejResp = claimService.rejectClaim("claim-1", rejReq);
        assertEquals(ClaimStatus.REJECTED, rejResp.getStatus());
        assertEquals("Tổn thất phát sinh ngoài phạm vi bảo hiểm (điều khoản 4.2)", rejResp.getRejectReason());
    }

    // 17. Chuyển trạng thái sai quy trình -> 400 Bad Request
    @Test
    void invalidStatusTransition_throws400() {
        when(permissionService.isAdmin()).thenReturn(true);
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));

        // sampleClaim đang là SUBMITTED -> gọi payClaim -> 400
        assertThrows(BadRequestException.class, () -> claimService.payClaim("claim-1"));

        // sampleClaim đang là SUBMITTED -> gọi approveClaim trực tiếp -> 400
        ApproveClaimRequest appReq = ApproveClaimRequest.builder().approvedAmount(1000000.0).build();
        assertThrows(BadRequestException.class, () -> claimService.approveClaim("claim-1", appReq));
    }

    // 18. Quyền thao tác duyệt: Non-ADMIN (CUSTOMER, AGENT, UW) gọi approve/reject/pay/review -> 403 Forbidden
    @Test
    void nonAdmin_cannotApproveRejectOrPay_throws403() {
        when(permissionService.isAdmin()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> claimService.reviewClaim("claim-1"));
        assertThrows(ForbiddenException.class, () -> claimService.approveClaim("claim-1", new ApproveClaimRequest()));
        assertThrows(ForbiddenException.class, () -> claimService.rejectClaim("claim-1", new RejectClaimRequest()));
        assertThrows(ForbiddenException.class, () -> claimService.payClaim("claim-1"));
    }

    // 19. Từ chối không có lý do -> 400 Bad Request
    @Test
    void reject_missingReason_throws400() {
        sampleClaim.setStatus(ClaimStatus.UNDER_REVIEW);
        when(permissionService.isAdmin()).thenReturn(true);
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));

        RejectClaimRequest emptyReq = RejectClaimRequest.builder().rejectReason("   ").build();
        assertThrows(BadRequestException.class, () -> claimService.rejectClaim("claim-1", emptyReq));
    }

    // 20. Phê duyệt với approvedAmount > claimedAmount -> 400 Bad Request
    @Test
    void approve_amountGreaterThanClaimed_throws400() {
        sampleClaim.setStatus(ClaimStatus.UNDER_REVIEW);
        sampleClaim.setClaimedAmount(2000000.0);
        when(permissionService.isAdmin()).thenReturn(true);
        when(claimRepository.findById("claim-1")).thenReturn(Optional.of(sampleClaim));

        ApproveClaimRequest overReq = ApproveClaimRequest.builder()
                .approvedAmount(2500000.0)
                .build();
        assertThrows(BadRequestException.class, () -> claimService.approveClaim("claim-1", overReq));
    }
}
