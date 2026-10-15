package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopPolicyReportDto {
    private String policyNumber;
    private String insuredName;
    private String status;
    private Double totalPremium;
    private Integer locationCount;
    private Integer coverageCount;
    private String effectiveDate;
}
