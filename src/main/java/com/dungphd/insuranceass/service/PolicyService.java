package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;

public interface PolicyService {
    PolicyResponse createPolicy(CreatePolicyRequest policyRequest);
    PolicyResponse getPolicyByNumber(String policyNumber);
    PolicyResponse updatePolicy(String policyNumber, UpdatePolicyRequest request);
}
