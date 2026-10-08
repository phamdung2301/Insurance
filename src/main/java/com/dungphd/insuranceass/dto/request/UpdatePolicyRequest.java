package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePolicyRequest {
    @Valid
    private InsuredDto insured;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant effectiveDate;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.dungphd.insuranceass.config.FlexibleInstantDeserializer.class)
    private Instant expirationDate;
}
