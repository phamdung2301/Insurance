package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.PolicyVersion;

import java.util.List;
import java.util.Map;

public interface EndorsementService {

    PolicyResponse applyEndorsement(String policyNumber, EndorsementRequest request, String actor);

    /**
     * Đợt 4B, luồng NỘI BỘ cho checker duyệt pending: apply đúng luồng
     * applyEndorsement nhưng BYPASS ngưỡng maker-checker (không rơi lại vào
     * pending) và KHÔNG chạy lại checkPolicyWrite của người yêu cầu (quyền
     * của checker đã được PendingEndorsementService enforce là UW trước khi
     * gọi). Chỉ PendingEndorsementServiceImpl được gọi method này; controller
     * công khai vẫn đi qua applyEndorsement (có ngưỡng).
     */
    PolicyResponse applyEndorsementBypassingThreshold(String policyNumber, EndorsementRequest request, String actor);

    List<PolicyVersion> getVersions(String policyNumber);

    PolicyVersion getVersion(String policyNumber, Integer version);

    Map<String, Object> compareVersions(String policyNumber, Integer v1, Integer v2);
}
