package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.PolicyVersion;

import java.util.List;
import java.util.Map;

public interface EndorsementService {

    PolicyResponse applyEndorsement(String policyNumber, EndorsementRequest request, String actor);

    List<PolicyVersion> getVersions(String policyNumber);

    PolicyVersion getVersion(String policyNumber, Integer version);

    Map<String, Object> compareVersions(String policyNumber, Integer v1, Integer v2);
}
