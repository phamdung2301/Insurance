package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPremiumReportDto {
    private String status;
    private Double totalPremium;
    private Long policyCount;
    private Double averagePremium;
}
