package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.request.StatusTransitionRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.PolicyVersion;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.PolicyService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/policies", "/api/policies"})
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final EndorsementService endorsementService;
    private final TransactionLedgerService transactionLedgerService;

    // ==========================================
    // Dynamic Search, Filter, Page & Sort (P02, P03)
    // ==========================================

        /**
     * GET /policies/mine
     * Hợp đồng của chính user đang đăng nhập (lấy email từ JWT, không tin client).
     * Thay thế việc tải toàn bộ rồi filter ở frontend.
     */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<Policy>>> getMyPolicies(
            @Valid @ModelAttribute PolicySearchCriteria criteria,
            Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập");
        }
        criteria.setInsuredEmail(principal.getName().trim().toLowerCase());
        PageResponse<Policy> page = policyService.searchPolicies(criteria);
        return ResponseEntity.ok(ApiResponse.success(page, "Danh sách hợp đồng của bạn"));
    }

@GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Policy>>> searchPolicies(
            @Valid @ModelAttribute PolicySearchCriteria criteria
    ) {
        PageResponse<Policy> response = policyService.searchPolicies(criteria);
        return ResponseEntity.ok(ApiResponse.success(response, "Policies retrieved successfully"));
    }

    // ==========================================
    // Policy Core CRUD (P01)
    // ==========================================

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyResponse>> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        PolicyResponse response = policyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Policy created successfully"));
    }

    @GetMapping("/{policyNumber}")
    public ResponseEntity<ApiResponse<PolicyResponse>> getPolicyByNumber(@PathVariable String policyNumber) {
        PolicyResponse response = policyService.getPolicyByNumber(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy retrieved successfully"));
    }

    @PutMapping("/{policyNumber}")
    public ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(
            @PathVariable String policyNumber,
            @Valid @RequestBody UpdatePolicyRequest request) {
        PolicyResponse response = policyService.updatePolicy(policyNumber, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy updated successfully"));
    }

    @DeleteMapping("/{policyNumber}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable String policyNumber) {
        policyService.deletePolicy(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(null, "Policy deleted successfully"));
    }

    // ==========================================
    // State Transitions (P06, P07)
    // ==========================================

    @PostMapping("/{policyNumber}/status-transitions")
    public ResponseEntity<ApiResponse<PolicyResponse>> transitionStatus(
            @PathVariable String policyNumber,
            @Valid @RequestBody StatusTransitionRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        PolicyResponse response = policyService.transitionStatus(policyNumber, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy status transitioned to " + request.getTargetStatus()));
    }

    // ==========================================
    // Endorsement Engine & History Versioning (P08)
    // ==========================================

    @PostMapping("/{policyNumber}/endorsements")
    public ResponseEntity<ApiResponse<PolicyResponse>> applyEndorsement(
            @PathVariable String policyNumber,
            @Valid @RequestBody EndorsementRequest request,
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {
        String actor = resolveActor(principal, headerEmail);
        PolicyResponse response = endorsementService.applyEndorsement(policyNumber, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy endorsed successfully to version " + response.getVersion()));
    }

    @GetMapping("/{policyNumber}/versions")
    public ResponseEntity<ApiResponse<List<PolicyVersion>>> getVersions(@PathVariable String policyNumber) {
        List<PolicyVersion> versions = endorsementService.getVersions(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(versions, "Policy versions retrieved successfully"));
    }

    @GetMapping("/{policyNumber}/versions/{version}")
    public ResponseEntity<ApiResponse<PolicyVersion>> getVersion(
            @PathVariable String policyNumber,
            @PathVariable Integer version) {
        PolicyVersion versionDoc = endorsementService.getVersion(policyNumber, version);
        return ResponseEntity.ok(ApiResponse.success(versionDoc, "Policy version snapshot retrieved successfully"));
    }

    @GetMapping("/{policyNumber}/versions/compare")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareVersions(
            @PathVariable String policyNumber,
            @RequestParam(name = "v1") Integer v1,
            @RequestParam(name = "v2") Integer v2) {
        Map<String, Object> comparison = endorsementService.compareVersions(policyNumber, v1, v2);
        return ResponseEntity.ok(ApiResponse.success(comparison, "Policy version comparison generated successfully"));
    }

    // ==========================================
    // Transaction Ledger History (P09)
    // ==========================================

    @GetMapping("/{policyNumber}/history")
    public ResponseEntity<ApiResponse<List<PolicyTransaction>>> getHistory(@PathVariable String policyNumber) {
        List<PolicyTransaction> history = transactionLedgerService.getTransactions(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(history, "Policy transaction history retrieved successfully"));
    }

    private String resolveActor(Principal principal, String headerEmail) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        if (headerEmail != null && !headerEmail.isBlank()) {
            return headerEmail.trim();
        }
        return "System";
    }
}
