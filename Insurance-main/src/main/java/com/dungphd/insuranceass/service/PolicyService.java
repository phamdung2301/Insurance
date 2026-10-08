package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.model.Policy;

public interface PolicyService {

    PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria);

    Policy getPolicyById(String policyIdOrNumber);

    Policy createPolicy(Policy policy);
}
