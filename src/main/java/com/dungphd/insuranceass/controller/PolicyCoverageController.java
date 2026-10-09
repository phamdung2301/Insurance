package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.service.PolicyCoverageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/policies/{policyId}/locations/{locationId}/coverages", "/api/policies/{policyId}/locations/{locationId}/coverages"})
@RequiredArgsConstructor
public class PolicyCoverageController {

    private final PolicyCoverageService policyCoverageService;

    /**
     * GET /policies/{policyId}/locations/{locationId}/coverages
     * Get all coverages inside a specific location of a policy
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Coverage>>> getCoverages(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") String locationId
    ) {
        List<Coverage> coverages = policyCoverageService.getCoverages(policyId, locationId);
        return ResponseEntity.ok(ApiResponse.success(coverages, "Coverages retrieved successfully"));
    }

    /**
     * GET /policies/{policyId}/locations/{locationId}/coverages/{coverageCode}
     * Get a specific coverage by code
     */
    @GetMapping("/{coverageCode}")
    public ResponseEntity<ApiResponse<Coverage>> getCoverageByCode(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") String locationId,
            @PathVariable("coverageCode") String coverageCode
    ) {
        Coverage coverage = policyCoverageService.getCoverageByCode(policyId, locationId, coverageCode);
        return ResponseEntity.ok(ApiResponse.success(coverage, "Coverage retrieved successfully"));
    }

    /**
     * POST /policies/{policyId}/locations/{locationId}/coverages
     * Add a nested Coverage into Location; auto-recalculates total premium
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Policy>> addCoverage(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") String locationId,
            @Valid @RequestBody CoverageDto coverageDto
    ) {
        Policy updatedPolicy = policyCoverageService.addCoverage(policyId, locationId, coverageDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(updatedPolicy, "Coverage added successfully to location"));
    }

    /**
     * PUT /policies/{policyId}/locations/{locationId}/coverages/{coverageCode}
     * Update an existing Coverage in Location; auto-recalculates total premium
     */
    @PutMapping("/{coverageCode}")
    public ResponseEntity<ApiResponse<Policy>> updateCoverage(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") String locationId,
            @PathVariable("coverageCode") String coverageCode,
            @Valid @RequestBody CoverageDto coverageDto
    ) {
        Policy updatedPolicy = policyCoverageService.updateCoverage(policyId, locationId, coverageCode, coverageDto);
        return ResponseEntity.ok(ApiResponse.success(updatedPolicy, "Coverage updated successfully in location"));
    }

    /**
     * DELETE /policies/{policyId}/locations/{locationId}/coverages/{coverageCode}
     * Remove a Coverage from Location; auto-recalculates total premium
     */
    @DeleteMapping("/{coverageCode}")
    public ResponseEntity<ApiResponse<Policy>> removeCoverage(
            @PathVariable("policyId") String policyId,
            @PathVariable("locationId") String locationId,
            @PathVariable("coverageCode") String coverageCode
    ) {
        Policy updatedPolicy = policyCoverageService.removeCoverage(policyId, locationId, coverageCode);
        return ResponseEntity.ok(ApiResponse.success(updatedPolicy, "Coverage removed successfully from location"));
    }
}
