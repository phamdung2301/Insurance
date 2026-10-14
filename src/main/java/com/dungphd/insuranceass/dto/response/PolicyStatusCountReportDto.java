package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyStatusCountReportDto {
    private String status;
    private Long count;
    private Double percentage;
}
