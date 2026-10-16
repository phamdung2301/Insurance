package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.PolicyVersion;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class EndorsementController {

    private final EndorsementService endorsementService;
    private final TransactionLedgerService transactionLedgerService;

    /**
     * POST /endorsements and POST /api/endorsements
     * Applies endorsement to an ACTIVE policy, incrementing version, taking snapshot, and recording transaction.
     * Policy number can be passed in request body or as request parameter.
     */
    @PostMapping({"/endorsements", "/api/endorsements"})
    public ResponseEntity<ApiResponse<PolicyResponse>> applyEndorsementDirect(
            @RequestParam(name = "policyNumber", required = false) String queryPolicyNumber,
            @Valid @RequestBody EndorsementRequest request,
            Principal principal) {

        String policyNumber = queryPolicyNumber;
        if (policyNumber == null || policyNumber.isBlank()) {
            policyNumber = request.getPolicyNumber();
        }
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new BadRequestException("policyNumber must be specified either as query param or in request body");
        }

        String actor = resolveActor(principal);
        PolicyResponse response = endorsementService.applyEndorsement(policyNumber, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy endorsed successfully to version " + response.getVersion()));
    }

    /**
     * GET /history and GET /api/history
     * Retrieve transaction ledger history for a policy.
     */
    @GetMapping({"/history", "/api/history"})
    public ResponseEntity<ApiResponse<List<PolicyTransaction>>> getHistoryDirect(
            @RequestParam(name = "policyNumber") String policyNumber) {
        List<PolicyTransaction> history = transactionLedgerService.getTransactions(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(history, "Policy transaction history retrieved successfully"));
    }

    /**
     * GET /versions and GET /api/versions
     * Retrieve all immutable historical versions for a policy.
     */
    @GetMapping({"/versions", "/api/versions"})
    public ResponseEntity<ApiResponse<List<PolicyVersion>>> getVersionsDirect(
            @RequestParam(name = "policyNumber") String policyNumber) {
        List<PolicyVersion> versions = endorsementService.getVersions(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(versions, "Policy versions retrieved successfully"));
    }

    /**
     * GET /versions/{version} and GET /api/versions/{version}
     * Retrieve a specific immutable historical version snapshot.
     */
    @GetMapping({"/versions/{version}", "/api/versions/{version}"})
    public ResponseEntity<ApiResponse<PolicyVersion>> getVersionDirect(
            @PathVariable Integer version,
            @RequestParam(name = "policyNumber") String policyNumber) {
        PolicyVersion versionDoc = endorsementService.getVersion(policyNumber, version);
        return ResponseEntity.ok(ApiResponse.success(versionDoc, "Policy version snapshot retrieved successfully"));
    }

    private String resolveActor(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        return "System";
    }
}
