package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coverage {
    private String coverageCode;
    private String coverageName;
    private String coverageType;
    private Double limit;
    private Double deductible;
    private Integer termMonths;
    private Double baseRate;
    private Double premium;

}
