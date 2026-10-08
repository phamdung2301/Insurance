package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/policies", "/api/policies"})
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    /**
     * GET /policies
     * Dynamic Search, Combined Filtering, Paging & Sorting (P02, P03)
     * Filters: status, insuredName, location, effectiveDateFrom/To, policyNumber, min/maxPremium
     * Paging & Sorting: page, size, sortBy, sortDirection
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Policy>>> searchPolicies(
            @ModelAttribute PolicySearchCriteria criteria
    ) {
        PageResponse<Policy> response = policyService.searchPolicies(criteria);
        return ResponseEntity.ok(ApiResponse.success(response, "Policies retrieved successfully"));
    }

    /**
     * GET /policies/{policyId}
     * Get single policy by MongoDB ID or policyNumber
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<ApiResponse<Policy>> getPolicyById(
            @PathVariable("policyId") String policyId
    ) {
        Policy policy = policyService.getPolicyById(policyId);
        return ResponseEntity.ok(ApiResponse.success(policy, "Policy retrieved successfully"));
    }

    /**
     * POST /policies
     * Create a new policy
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Policy>> createPolicy(
            @RequestBody Policy policy
    ) {
        Policy createdPolicy = policyService.createPolicy(policy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdPolicy, "Policy created successfully"));
    }
}
