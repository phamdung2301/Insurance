package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateMatrixItemDto {

    private String coverageCode;
    private String coverageName;
    private String coverageType;
    private Double baseRate;
    private Double minLimit;
    private Double maxLimit;
    private Double defaultDeductible;
    private String description;
}
