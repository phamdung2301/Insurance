package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ApproveClaimRequest;
import com.dungphd.insuranceass.dto.request.CreateClaimRequest;
import com.dungphd.insuranceass.dto.request.RejectClaimRequest;
import com.dungphd.insuranceass.dto.response.ClaimResponse;
import com.dungphd.insuranceass.model.ClaimPhoto;
import com.dungphd.insuranceass.model.ClaimStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClaimService {

    ClaimResponse createClaim(CreateClaimRequest request, List<MultipartFile> photos);

    ClaimResponse addPhotos(String claimId, List<MultipartFile> photos);

    Page<ClaimResponse> listClaims(ClaimStatus status, String policyNumber, Pageable pageable);

    ClaimResponse getClaimById(String claimId);

    Resource getClaimPhoto(String claimId, String photoId);

    ClaimPhoto getPhotoMetadata(String claimId, String photoId);

    ClaimResponse reviewClaim(String claimId);

    /**
     * API tiếp nhận bồi thường mới (POST /claims/{id}/intake): chỉ nhân viên
     * xử lý (UNDERWRITER hoặc ADMIN) được xác nhận hồ sơ của khách hàng.
     * Khách hàng không bao giờ được tự xác nhận hồ sơ của chính mình.
     */
    ClaimResponse intakeClaim(String claimId);

    ClaimResponse approveClaim(String claimId, ApproveClaimRequest request);

    ClaimResponse rejectClaim(String claimId, RejectClaimRequest request);

    ClaimResponse payClaim(String claimId);
}

