package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.model.PolicyStatus;
import jakarta.validation.constraints.NotBlank;
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
public class CreateEndorsementRequest {

    @NotBlank(message = "Endorsement reason is required")
    private String endorsementReason;

    private String endorsementType; // e.g. GENERAL, FINANCIAL, ADDRESS_CHANGE, COVERAGE_ADJUSTMENT

    private Instant endorsementEffectiveDate;

    private String actor;

    private InsuredDto insured;

    private List<LocationDto> locations;

    private Instant effectiveDate;

    private Instant expirationDate;

    private PolicyStatus status;
}
