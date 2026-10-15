package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
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
public class EndorsementRequest {

    private String policyNumber;

    @NotBlank(message = "Endorsement reason is required")
    private String reason;

    private String description;

    private InsuredDto insured;

    private List<LocationDto> locations;

    private Instant effectiveDate;

    private Instant expirationDate;
}
