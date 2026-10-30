package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.PendingEndorsement;
import com.dungphd.insuranceass.service.PendingEndorsementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Maker-checker cho endorsement (đợt 4B). Dual mapping /pending-endorsements
 * và /api/pending-endorsements theo quy ước repo.
 * Role chặn ở service (PendingEndorsementServiceImpl), matcher SecurityConfig
 * chỉ yêu cầu authenticated vì danh sách có scope theo role.
 */
@RestController
@RequestMapping({"/pending-endorsements", "/api/pending-endorsements"})
@RequiredArgsConstructor
public class PendingEndorsementController {

    private final PendingEndorsementService pendingEndorsementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PendingEndorsement>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success(
                pendingEndorsementService.listPendingForCurrentUser(),
                "Danh sách endorsement chờ duyệt."));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PendingEndorsement>> approve(
            @PathVariable String id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                pendingEndorsementService.approve(id, resolveActor(principal)),
                "Endorsement đã được duyệt và áp dụng."));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PendingEndorsement>> reject(
            @PathVariable String id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                pendingEndorsementService.reject(id, resolveActor(principal)),
                "Endorsement đã bị từ chối, hợp đồng không thay đổi."));
    }

    private String resolveActor(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        return "System";
    }
}
