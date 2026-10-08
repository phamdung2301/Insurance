package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("policies")
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyService policyService;

    // ==========================================
    // 2.1 Policy Core CRUD (P01) - Create
    // ==========================================

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyResponse>> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        PolicyResponse response = policyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Policy created successfully"));
    }

    // ==========================================
    // 2.1 Policy Core CRUD (P01) - Read
    // ==========================================

    @GetMapping("/{policyNumber}")
    public ResponseEntity<ApiResponse<PolicyResponse>> getPolicyByNumber(@PathVariable String policyNumber) {
        PolicyResponse response = policyService.getPolicyByNumber(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy retrieved successfully"));
    }

    // ==========================================
    // 2.1 Policy Core CRUD (P01) - Update
    // ==========================================

    @PutMapping("/{policyNumber}")
    public ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(
            @PathVariable String policyNumber,
            @Valid @RequestBody UpdatePolicyRequest request) {
        PolicyResponse response = policyService.updatePolicy(policyNumber, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy updated successfully"));
    }
}
