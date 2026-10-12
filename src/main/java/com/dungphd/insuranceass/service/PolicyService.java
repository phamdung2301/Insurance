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

    PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria);
    Policy getPolicyById(String policyIdOrNumber);
    Policy createPolicy(Policy policy);
}
