package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.request.StatusTransitionRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.Policy;

public interface PolicyService {
    PolicyResponse createPolicy(CreatePolicyRequest policyRequest);
    PolicyResponse getPolicyByNumber(String policyNumber);
    PolicyResponse updatePolicy(String policyNumber, UpdatePolicyRequest request);
    void deletePolicy(String policyNumber);

    PolicyResponse transitionStatus(String policyNumber, StatusTransitionRequest request, String actor);

    /** Nút "Tôi đã chuyển khoản": chỉ set marker, policy giữ BOUND (PLAN §1.6). Idempotent. */
    PolicyResponse claimPayment(String policyNumber, String actor);

    PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria);

    /**
     * GET /policies/mine — Track B mục 3: chỉ hợp đồng mà caller là người
     * được bảo hiểm ({@code insured.email = principalEmail}), không qua
     * applySearchScope (tránh bug AND của nhánh agent cũ).
     */
    PageResponse<Policy> searchMyPolicies(PolicySearchCriteria criteria, String principalEmail);
    Policy getPolicyById(String policyIdOrNumber);
    Policy createPolicy(Policy policy);
}
