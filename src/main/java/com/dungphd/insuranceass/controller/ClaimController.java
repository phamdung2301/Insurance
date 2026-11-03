package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.ApproveClaimRequest;
import com.dungphd.insuranceass.dto.request.CreateClaimRequest;
import com.dungphd.insuranceass.dto.request.RejectClaimRequest;
import com.dungphd.insuranceass.dto.response.ClaimResponse;
import com.dungphd.insuranceass.model.ClaimPhoto;
import com.dungphd.insuranceass.model.ClaimStatus;
import com.dungphd.insuranceass.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/claims", "/api/claims"})
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimResponse> createClaim(
            @ModelAttribute @Valid CreateClaimRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos
    ) {
        ClaimResponse response = claimService.createClaim(request, photos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimResponse> addPhotos(
            @PathVariable("id") String id,
            @RequestPart("photos") List<MultipartFile> photos
    ) {
        return ResponseEntity.ok(claimService.addPhotos(id, photos));
    }

    @GetMapping
    public ResponseEntity<Page<ClaimResponse>> getClaims(
            @RequestParam(value = "status", required = false) ClaimStatus status,
            @RequestParam(value = "policyNumber", required = false) String policyNumber,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(claimService.listClaims(status, policyNumber, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaimById(@PathVariable("id") String id) {
        return ResponseEntity.ok(claimService.getClaimById(id));
    }

    @GetMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Resource> getPhotoResource(
            @PathVariable("id") String id,
            @PathVariable("photoId") String photoId
    ) {
        Resource resource = claimService.getClaimPhoto(id, photoId);
        ClaimPhoto metadata = claimService.getPhotoMetadata(id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType() != null ? metadata.getContentType() : "image/jpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ClaimResponse> updateStatusToReview(@PathVariable("id") String id) {
        return ResponseEntity.ok(claimService.reviewClaim(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable("id") String id,
            @Valid @RequestBody ApproveClaimRequest request
    ) {
        return ResponseEntity.ok(claimService.approveClaim(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable("id") String id,
            @Valid @RequestBody RejectClaimRequest request
    ) {
        return ResponseEntity.ok(claimService.rejectClaim(id, request));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ClaimResponse> payClaim(@PathVariable("id") String id) {
        return ResponseEntity.ok(claimService.payClaim(id));
    }
}
