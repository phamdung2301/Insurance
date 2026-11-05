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
import java.io.InputStream;
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

    /** Đợt payment — optional như EndorsementServiceImpl: test cũ không có mock này vẫn chạy (null-check). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dungphd.insuranceass.service.NotificationService notificationService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_TOTAL_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_PHOTOS_PER_CLAIM = 10;
    // FIX 10(a): extension -> content-type chuẩn của đúng loại đó. Một file
    // chỉ hợp lệ khi extension, content-type VÀ magic bytes đều khớp cùng
    // một loại (không còn chấp nhận OR như trước). "image/pjpeg" cũ bị loại
    // vì không nằm trong allowlist đã duyệt (jpg/jpeg dùng image/jpeg).
    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
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

    /**
     * FIX 10(a): định dạng ảnh phải thỏa CẢ BA, không còn OR như trước —
     * (1) extension trong allowlist, (2) content-type khớp đúng loại của
     * extension đó, (3) magic bytes khớp đúng loại đó. Trượt 1 trong 3 là
     * reject cả file với message nêu rõ điểm trượt. Các rule khác giữ
     * nguyên: tối đa 10 ảnh/claim, tối đa 10MB/ảnh.
     */
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

        String expectedContentType = EXTENSION_TO_CONTENT_TYPE.get(ext);
        if (expectedContentType == null) {
            throw new BadRequestException("File '" + filename + "' không phải định dạng ảnh hợp lệ (chỉ hỗ trợ jpg, jpeg, png, gif, webp).");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase(expectedContentType)) {
            throw new BadRequestException("File '" + filename + "' có content-type '" + contentType
                    + "' không khớp với định dạng ." + ext + " (cần " + expectedContentType + ").");
        }

        if (!hasMatchingMagicBytes(file, ext)) {
            throw new BadRequestException("File '" + filename + "' có nội dung không khớp với định dạng ."
                    + ext + " (magic bytes không đúng loại ảnh đã khai báo).");
        }
    }

    /**
     * Đọc tối đa 12 byte đầu file và đối chiếu magic bytes của đúng loại
     * theo extension: JPEG FF D8 FF; PNG 89 50 4E 47 0D 0A 1A 0A; GIF "GIF8";
     * WEBP byte 0-3 "RIFF" và byte 8-11 "WEBP". Không đọc được header
     * (stream null, lỗi IO, file ngắn hơn header tối thiểu) cũng là trượt.
     * Mỗi lần gọi getInputStream() là một stream mới nên lần đọc này không
     * làm cạn stream mà savePhotoToDisk dùng sau đó.
     */
    private boolean hasMatchingMagicBytes(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            if (in == null) {
                return false;
            }
            read = 0;
            int n;
            while (read < header.length && (n = in.read(header, read, header.length - read)) != -1) {
                read += n;
            }
        } catch (IOException e) {
            return false;
        }
        switch (ext) {
            case "jpg":
            case "jpeg":
                return read >= 3
                        && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png":
                return read >= 8
                        && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                        && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
            case "gif":
                return read >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
            case "webp":
                return read >= 12
                        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default:
                return false;
        }
    }

    /**
     * FIX 10(b): giới hạn 50MB tính CỘNG DỒN — tổng dung lượng ảnh đã có của
     * claim (currentTotalSize, cộng từ ClaimPhoto.fileSize, null tính là 0)
     * + tổng dung lượng file mới. Vượt là reject CẢ batch, không nhận một
     * phần. Vẫn validate từng file trước (định dạng, 10MB/ảnh) rồi mới
     * chốt tổng, và vẫn giữ giới hạn 10 ảnh/claim (đã có + mới).
     */
    private void validatePhotosList(List<MultipartFile> photos, int currentCount, long currentTotalSize) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        if (currentCount + photos.size() > MAX_PHOTOS_PER_CLAIM) {
            throw new BadRequestException("Tối đa " + MAX_PHOTOS_PER_CLAIM + " ảnh cho một yêu cầu bồi thường (hiện có "
                    + currentCount + " ảnh, gửi thêm " + photos.size() + " ảnh).");
        }
        long newTotalSize = 0;
        for (MultipartFile photo : photos) {
            validateImageFile(photo);
            newTotalSize += photo.getSize();
        }
        long existingSize = Math.max(0L, currentTotalSize);
        if (existingSize + newTotalSize > MAX_TOTAL_SIZE) {
            throw new BadRequestException("Tổng dung lượng ảnh của yêu cầu bồi thường vượt quá giới hạn 50MB (đã có "
                    + formatMb(existingSize) + "MB, gửi thêm " + formatMb(newTotalSize)
                    + "MB, tổng " + formatMb(existingSize + newTotalSize)
                    + "MB) — toàn bộ batch bị từ chối, không nhận một phần.");
        }
    }

    /** Tổng fileSize ảnh đã lưu của claim; fileSize null tính là 0. */
    private static long sumExistingPhotoSizes(List<ClaimPhoto> photos) {
        if (photos == null) {
            return 0L;
        }
        long sum = 0L;
        for (ClaimPhoto photo : photos) {
            if (photo != null && photo.getFileSize() != null) {
                sum += photo.getFileSize();
            }
        }
        return sum;
    }

    private static String formatMb(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
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
        // FINANCE chỉ làm việc với các API tiền — không được tạo yêu cầu bồi
        // thường (đó là việc của bên cầu). ADMIN kiêm FINANCE vẫn được miễn
        // như mọi gate role khác trong hệ thống.
        if (permissionService.hasFinanceRole() && !permissionService.isAdmin()) {
            throw new ForbiddenException("Vai trò Kế toán (FINANCE) chỉ làm việc với các API tiền, không được tạo yêu cầu bồi thường.");
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

        validatePhotosList(photos, 0, 0L);

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
        // FIX 10(b): cộng dồn dung lượng ảnh đã có (ClaimPhoto.fileSize) vào
        // giới hạn 50MB, không chỉ tính riêng batch mới gửi lên.
        validatePhotosList(photos, currentCount, sumExistingPhotoSizes(claim.getPhotos()));

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

    /**
     * Tiếp nhận bồi thường (POST /claims/{id}/intake) — API mới, tách khỏi
     * reviewClaim cũ (giữ nguyên endpoint/method cũ cho caller/test cũ).
     * Chỉ UNDERWRITER hoặc ADMIN; CUSTOMER (kể cả chính claimant) và AGENT
     * đều 403. SUBMITTED -> UNDER_REVIEW, ghi history (changedBy = người
     * xử lý) + audit INTAKE_CLAIM. Claim không có field handler/assignedTo
     * riêng nên history changedBy chính là bản ghi người xử lý.
     */
    @Override
    public ClaimResponse intakeClaim(String claimId) {
        checkStaff();
        String principal = permissionService.currentPrincipal();
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bồi thường: " + claimId));

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BadRequestException("Chỉ có thể tiếp nhận thẩm định từ trạng thái SUBMITTED (trạng thái hiện tại: " + claim.getStatus() + ").");
        }

        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim.setUpdatedAt(Instant.now());
        if (claim.getStatusHistory() == null) {
            claim.setStatusHistory(new ArrayList<>());
        }
        claim.getStatusHistory().add(ClaimStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .fromStatus(ClaimStatus.SUBMITTED)
                .toStatus(ClaimStatus.UNDER_REVIEW)
                .changedBy(principal)
                .changedAt(Instant.now())
                .note("Tiếp nhận thẩm định")
                .build());

        Claim saved = claimRepository.save(claim);
        log.info("Claim {} intake by {}", claimId, principal);
        recordAudit(principal, "INTAKE_CLAIM", "ClaimId: " + claimId);
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
        // Đợt payment — mail + in-app "claim được approve", CHỈ ở bước approve
        // này (không gắn ở create/review/reject/pay). Đặt sau save thành công;
        // NotificationService failure-isolated, bọc thêm try/catch để lookup
        // policy không bao giờ phá approve. Người nhận: policyRecipients nếu
        // tra được policy, fallback claimantEmail (xử lý trong impl).
        if (notificationService != null) {
            try {
                com.dungphd.insuranceass.model.Policy policy = policyRepository
                        .findByPolicyNumber(saved.getPolicyNumber())
                        .or(() -> policyRepository.findById(saved.getPolicyNumber()))
                        .orElse(null);
                notificationService.notifyClaimApproved(saved, policy);
            } catch (Exception e) {
                log.warn("Failed to send claim-approved notification for claim {}: {}", claimId, e.getMessage());
            }
        }
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
        // Chi trả bồi thường là tiền ra: FINANCE thực hiện sau khi ADMIN đã
        // duyệt số tiền (maker-checker: duyệt ≠ chi trả). Duyệt/từ chối/
        // review/intake vẫn chỉ ADMIN (hoặc UW theo checkStaff).
        checkFinanceOrAdmin();
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

    /** Chi trả bồi thường (tiền ra): FINANCE hoặc ADMIN. */
    private void checkFinanceOrAdmin() {
        if (!permissionService.isAdmin() && !permissionService.hasFinanceRole()) {
            throw new ForbiddenException("Chỉ Kế toán (FINANCE) hoặc Quản trị viên (ADMIN) mới được đánh dấu chi trả bồi thường.");
        }
    }

    /** Nhân viên xử lý bồi thường: UNDERWRITER hoặc ADMIN (pattern checkAdmin). */
    private void checkStaff() {
        if (!permissionService.isAdmin() && !permissionService.isUnderwriter()) {
            throw new ForbiddenException("Chỉ nhân viên xử lý (UNDERWRITER hoặc ADMIN) mới được tiếp nhận yêu cầu bồi thường.");
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

