package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.model.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyResponse {
    private String id;
    private String policyNumber;
    private PolicyStatus status;
    private InsuredDto insured;
    private List<LocationDto> locations;
    private Double totalPremium;
    private Instant effectiveDate;
    private Instant expirationDate;
    private Instant boundDate;
    private Instant paymentDueDate;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
