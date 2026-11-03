package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.ApproveClaimRequest;
import com.dungphd.insuranceass.dto.request.CreateClaimRequest;
import com.dungphd.insuranceass.dto.request.RejectClaimRequest;
import com.dungphd.insuranceass.dto.response.ClaimResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AdminAuditLogRepository;
import com.dungphd.insuranceass.repository.ClaimRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.ClaimService;
import com.dungphd.insuranceass.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AdminAuditLogRepository auditLogRepository;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_TOTAL_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_PHOTOS_PER_CLAIM = 10;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/pjpeg"
    );

    private Path getUploadDirectory() {
        String customDir = System.getenv("CLAIM_UPLOAD_DIR");
        Path uploadPath = (customDir != null && !customDir.isBlank())
                ? Paths.get(customDir)
                : Paths.get("uploads", "claims");
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            log.error("Không thể tạo thư mục lưu ảnh claim: {}", uploadPath, e);
            throw new RuntimeException("Lỗi hệ thống lưu trữ ảnh: " + e.getMessage());
        }
        return uploadPath;
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File ảnh rỗng hoặc không tồn tại.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Ảnh '" + file.getOriginalFilename() + "' vượt quá dung lượng tối đa cho phép là 10MB.");
        }

        String filename = file.getOriginalFilename();
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }

        String contentType = file.getContentType();
        boolean validExt = ALLOWED_IMAGE_EXTENSIONS.contains(ext);
        boolean validContentType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());

        if (!validExt && !validContentType) {
            throw new BadRequestException("File '" + filename + "' không phải định dạng ảnh hợp lệ (chỉ hỗ trợ jpg, jpeg, png, webp).");
        }
    }

    private void validatePhotosList(List<MultipartFile> photos, int currentCount) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        if (currentCount + photos.size() > MAX_PHOTOS_PER_CLAIM) {
            throw new BadRequestException("Tối đa " + MAX_PHOTOS_PER_CLAIM + " ảnh cho một yêu cầu bồi thường (hiện có "
                    + currentCount + " ảnh, gửi thêm " + photos.size() + " ảnh).");
        }
        long totalSize = 0;
        for (MultipartFile photo : photos) {
            validateImageFile(photo);
            totalSize += photo.getSize();
        }
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BadRequestException("Tổng dung lượng các ảnh gửi lên vượt quá giới hạn 50MB.");
        }
    }

    private ClaimPhoto savePhotoToDisk(String claimId, MultipartFile file) {
        Path uploadDir = getUploadDirectory();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String photoId = UUID.randomUUID().toString();
        String storedFilename = photoId + "_" + safeName;
        Path targetPath = uploadDir.resolve(storedFilename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Lỗi khi lưu ảnh claim lên đĩa: {}", targetPath, e);
            throw new RuntimeException("Không thể lưu ảnh: " + e.getMessage());
        }

        return ClaimPhoto.builder()
                .id(photoId)
                .claimId(claimId)
                .fileName(originalFilename)
                .filePath(targetPath.toAbsolutePath().toString())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .uploadedAt(Instant.now())
                .build();
    }

    private Policy resolveActivePolicyForClaimant(String policyNumber, String principal) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng bảo hiểm với số: " + policyNumber));

        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        if (insuredEmail == null || !insuredEmail.trim().equalsIgnoreCase(principal.trim())) {
            throw new ForbiddenException("Bạn chỉ có thể yêu cầu bồi thường cho hợp đồng bảo hiểm của chính mình.");
        }

        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new ForbiddenException("Chỉ có thể yêu cầu bồi thường cho hợp đồng đang có hiệu lực (ACTIVE). Trạng thái hiện tại: " + policy.getStatus());
        }

        return policy;
    }

    private void checkClaimReadPermission(Claim claim) {
        String principal = permissionService.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Bạn không có quyền xem yêu cầu bồi thường này.");
        }
        if (permissionService.isAdmin() || permissionService.isUnderwriter()) {
            return;
        }
        if (claim.getClaimantEmail() != null && claim.getClaimantEmail().trim().equalsIgnoreCase(principal.trim())) {
            return;
        }

        Policy policy = policyRepository.findByPolicyNumber(claim.getPolicyNumber()).orElse(null);
        if (policy != null && policy.getAssignedAgentEmail() != null
                && policy.getAssignedAgentEmail().trim().equalsIgnoreCase(principal.trim())) {
            return;
        }

        throw new ForbiddenException("Bạn không có quyền xem yêu cầu bồi thường này.");
    }

    @Override
    public ClaimResponse createClaim(CreateClaimRequest request, List<MultipartFile> photos) {
        String principal = permissionService.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Vui lòng đăng nhập để yêu cầu bồi thường.");
        }

        Policy policy = resolveActivePolicyForClaimant(request.getPolicyNumber(), principal);

        if (request.getClaimedAmount() == null || request.getClaimedAmount() <= 0) {
            throw new BadRequestException("Số tiền yêu cầu bồi thường phải lớn hơn 0.");
        }
        if (request.getDamageDescription() == null || request.getDamageDescription().isBlank()) {
            throw new BadRequestException("Mô tả chi tiết thiệt hại là bắt buộc.");
        }
        if (request.getDamageDescription().length() > 5000) {
            throw new BadRequestException("Mô tả chi tiết thiệt hại tối đa 5000 ký tự.");
        }

        validatePhotosList(photos, 0);

        String claimId = UUID.randomUUID().toString();
        List<ClaimPhoto> savedPhotos = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                if (!photo.isEmpty()) {
                    savedPhotos.add(savePhotoToDisk(claimId, photo));
                }
            }
        }

        ClaimStatusHistory initialHistory = ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(null)
                .toStatus(ClaimStatus.SUBMITTED)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Khởi tạo yêu cầu bồi thường")
                .build();

        Claim claim = Claim.builder()
                .id(claimId)
                .policyNumber(policy.getPolicyNumber())
                .claimantEmail(principal)
                .damageDescription(request.getDamageDescription().trim())
                .claimedAmount(request.getClaimedAmount())
                .status(ClaimStatus.SUBMITTED)
                .photos(savedPhotos)
                .statusHistory(new ArrayList<>(List.of(initialHistory)))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Claim saved = claimRepository.save(claim);
        log.info("Created claim {} for policy {} by {}", saved.getId(), saved.getPolicyNumber(), principal);
        recordAudit(principal, "CREATE_CLAIM", "Policy: " + policy.getPolicyNumber() + ", amount: " + request.getClaimedAmount());
        return mapToResponse(saved);
    }

    @Override
    public ClaimResponse addPhotos(String claimId, List<MultipartFile> photos) {
        String principal = permissionService.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Vui lòng đăng nhập.");
        }

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (!claim.getClaimantEmail().trim().equalsIgnoreCase(principal.trim())) {
            throw new ForbiddenException("Chỉ người tạo yêu cầu bồi thường mới có quyền bổ sung ảnh bằng chứng.");
        }

        if (claim.getStatus() != ClaimStatus.SUBMITTED && claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BadRequestException("Chỉ có thể bổ sung ảnh khi yêu cầu đang ở trạng thái SUBMITTED hoặc UNDER_REVIEW.");
        }

        int currentCount = claim.getPhotos() != null ? claim.getPhotos().size() : 0;
        validatePhotosList(photos, currentCount);

        if (claim.getPhotos() == null) {
            claim.setPhotos(new ArrayList<>());
        }

        if (photos != null) {
            for (MultipartFile photo : photos) {
                if (!photo.isEmpty()) {
                    claim.getPhotos().add(savePhotoToDisk(claim.getId(), photo));
                }
            }
        }
        claim.setUpdatedAt(Instant.now());
        Claim saved = claimRepository.save(claim);
        log.info("Added {} photos to claim {} by {}", (photos != null ? photos.size() : 0), claimId, principal);
        return mapToResponse(saved);
    }

    @Override
    public Page<ClaimResponse> listClaims(ClaimStatus status, String policyNumber, Pageable pageable) {
        String principal = permissionService.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Vui lòng đăng nhập.");
        }

        if (permissionService.isAdmin() || permissionService.isUnderwriter()) {
            if (policyNumber != null && !policyNumber.isBlank()) {
                return claimRepository.findByPolicyNumberOrderByCreatedAtDesc(policyNumber.trim(), pageable)
                        .map(this::mapToResponse);
            }
            if (status != null) {
                return claimRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                        .map(this::mapToResponse);
            }
            return claimRepository.findAllByOrderByCreatedAtDesc(pageable)
                    .map(this::mapToResponse);
        }

        if (permissionService.hasCustomerRole()) {
            if (status != null) {
                return claimRepository.findByClaimantEmailAndStatusOrderByCreatedAtDesc(principal, status, pageable)
                        .map(this::mapToResponse);
            }
            return claimRepository.findByClaimantEmailOrderByCreatedAtDesc(principal, pageable)
                    .map(this::mapToResponse);
        }

        if (permissionService.hasAgentRole()) {
            List<Policy> agentPolicies = policyRepository.findByAssignedAgentEmail(principal);
            List<String> policyNumbers = agentPolicies.stream().map(Policy::getPolicyNumber).toList();
            if (policyNumbers.isEmpty()) {
                return Page.empty(pageable);
            }
            if (status != null) {
                return claimRepository.findByPolicyNumberInAndStatusOrderByCreatedAtDesc(policyNumbers, status, pageable)
                        .map(this::mapToResponse);
            }
            return claimRepository.findByPolicyNumberInOrderByCreatedAtDesc(policyNumbers, pageable)
                    .map(this::mapToResponse);
        }

        return Page.empty(pageable);
    }

    @Override
    public ClaimResponse getClaimById(String claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));
        checkClaimReadPermission(claim);
        return mapToResponse(claim);
    }

    @Override
    public Resource getClaimPhoto(String claimId, String photoId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));
        checkClaimReadPermission(claim);

        ClaimPhoto photo = claim.getPhotos().stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh: " + photoId));

        try {
            Path filePath = Paths.get(photo.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File ảnh không tồn tại trên hệ thống lưu trữ: " + photo.getFileName());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Đường dẫn file ảnh không hợp lệ: " + e.getMessage());
        }
    }

    @Override
    public ClaimPhoto getPhotoMetadata(String claimId, String photoId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));
        checkClaimReadPermission(claim);

        return claim.getPhotos().stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh: " + photoId));
    }

    @Override
    public ClaimResponse reviewClaim(String claimId) {
        checkAdmin();
        String principal = permissionService.currentPrincipal();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BadRequestException("Chỉ có thể chuyển sang Đang xem xét từ trạng thái SUBMITTED (trạng thái hiện tại: " + claim.getStatus() + ").");
        }

        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim.setUpdatedAt(Instant.now());
        claim.getStatusHistory().add(ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(ClaimStatus.SUBMITTED)
                .toStatus(ClaimStatus.UNDER_REVIEW)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Chuyển sang trạng thái đang xem xét hồ sơ")
                .build());

        Claim saved = claimRepository.save(claim);
        log.info("Claim {} reviewed by {}", claimId, principal);
        recordAudit(principal, "REVIEW_CLAIM", "ClaimId: " + claimId);
        return mapToResponse(saved);
    }

    @Override
    public ClaimResponse approveClaim(String claimId, ApproveClaimRequest request) {
        checkAdmin();
        String principal = permissionService.currentPrincipal();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu bồi thường khi đang ở trạng thái UNDER_REVIEW (trạng thái hiện tại: " + claim.getStatus() + ").");
        }

        if (request.getApprovedAmount() == null || request.getApprovedAmount() <= 0) {
            throw new BadRequestException("Số tiền duyệt bồi thường phải lớn hơn 0.");
        }
        if (request.getApprovedAmount() > claim.getClaimedAmount()) {
            throw new BadRequestException("Số tiền duyệt bồi thường (" + request.getApprovedAmount() + " VND) không được lớn hơn số tiền yêu cầu (" + claim.getClaimedAmount() + " VND).");
        }

        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(request.getApprovedAmount());
        claim.setHandlerNote(request.getHandlerNote());
        claim.setUpdatedAt(Instant.now());
        claim.getStatusHistory().add(ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(ClaimStatus.UNDER_REVIEW)
                .toStatus(ClaimStatus.APPROVED)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Duyệt bồi thường số tiền: " + request.getApprovedAmount() + (request.getHandlerNote() != null ? " - " + request.getHandlerNote() : ""))
                .build());

        Claim saved = claimRepository.save(claim);
        log.info("Claim {} approved by {} with amount {}", claimId, principal, request.getApprovedAmount());
        recordAudit(principal, "APPROVE_CLAIM", "ClaimId: " + claimId + ", amount: " + request.getApprovedAmount());
        return mapToResponse(saved);
    }

    @Override
    public ClaimResponse rejectClaim(String claimId, RejectClaimRequest request) {
        checkAdmin();
        String principal = permissionService.currentPrincipal();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BadRequestException("Chỉ có thể từ chối yêu cầu bồi thường khi đang ở trạng thái UNDER_REVIEW (trạng thái hiện tại: " + claim.getStatus() + ").");
        }

        if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
            throw new BadRequestException("Lý do từ chối bồi thường là bắt buộc.");
        }

        claim.setStatus(ClaimStatus.REJECTED);
        claim.setRejectReason(request.getRejectReason().trim());
        claim.setUpdatedAt(Instant.now());
        claim.getStatusHistory().add(ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(ClaimStatus.UNDER_REVIEW)
                .toStatus(ClaimStatus.REJECTED)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Từ chối bồi thường: " + request.getRejectReason().trim())
                .build());

        Claim saved = claimRepository.save(claim);
        log.info("Claim {} rejected by {} with reason: {}", claimId, principal, request.getRejectReason());
        recordAudit(principal, "REJECT_CLAIM", "ClaimId: " + claimId + ", reason: " + request.getRejectReason());
        return mapToResponse(saved);
    }

    @Override
    public ClaimResponse payClaim(String claimId) {
        checkAdmin();
        String principal = permissionService.currentPrincipal();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể đánh dấu đã chi trả từ trạng thái APPROVED (trạng thái hiện tại: " + claim.getStatus() + ").");
        }

        claim.setStatus(ClaimStatus.PAID);
        claim.setUpdatedAt(Instant.now());
        claim.getStatusHistory().add(ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(ClaimStatus.APPROVED)
                .toStatus(ClaimStatus.PAID)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Đã hoàn tất chi trả bồi thường")
                .build());

        Claim saved = claimRepository.save(claim);
        log.info("Claim {} paid by {}", claimId, principal);
        recordAudit(principal, "PAY_CLAIM", "ClaimId: " + claimId);
        return mapToResponse(saved);
    }

    private void checkAdmin() {
        if (!permissionService.isAdmin()) {
            throw new ForbiddenException("Chỉ Quản trị viên (ADMIN) mới có quyền xử lý yêu cầu bồi thường.");
        }
    }

    private ClaimResponse mapToResponse(Claim claim) {
        return ClaimResponse.builder()
                .id(claim.getId())
                .policyNumber(claim.getPolicyNumber())
                .claimantEmail(claim.getClaimantEmail())
                .damageDescription(claim.getDamageDescription())
                .claimedAmount(claim.getClaimedAmount())
                .approvedAmount(claim.getApprovedAmount())
                .status(claim.getStatus())
                .rejectReason(claim.getRejectReason())
                .handlerNote(claim.getHandlerNote())
                .photos(claim.getPhotos() != null ? List.copyOf(claim.getPhotos()) : List.of())
                .statusHistory(claim.getStatusHistory() != null ? List.copyOf(claim.getStatusHistory()) : List.of())
                .createdAt(claim.getCreatedAt())
                .updatedAt(claim.getUpdatedAt())
                .build();
    }

    private void recordAudit(String actor, String action, String details) {
        if (auditLogRepository != null) {
            try {
                auditLogRepository.save(AdminAuditLog.builder()
                        .actorEmail(actor != null ? actor : "System")
                        .targetEmail(details)
                        .action(action)
                        .createdAt(Instant.now())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to record audit log: {}", e.getMessage());
            }
        }
    }
}

