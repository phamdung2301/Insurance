package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyPremiumReportDto {
    private Integer year;
    private Integer month;
    private String monthLabel;
    private Double totalPremium;
    private Long policyCount;
}
