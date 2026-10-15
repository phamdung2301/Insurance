package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyVersionResponse {
    private String id;
    private String policyNumber;
    private Integer version;
    private String endorsementReason;
    private String endorsementType;
    private Instant endorsementEffectiveDate;
    private Double premiumChange;
    private Double totalPremium;
    private PolicyStatus status;
    private Policy policySnapshot;
    private String createdBy;
    private Instant createdAt;
}
