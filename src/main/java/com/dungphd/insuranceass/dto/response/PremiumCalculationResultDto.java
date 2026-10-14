package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PremiumCalculationResultDto {

    private String coverageCode;
    private String coverageName;
    private Double limit;
    private Double deductible;
    private Integer termMonths;
    private Double baseRate;
    private Double appliedRate;
    private Double calculatedPremium;
    private String calculationFormula;
    private String calculationTimestamp;
}
