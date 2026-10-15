package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionDiffResponse {
    private String policyNumber;
    private Integer fromVersion;
    private Integer toVersion;
    private Double premiumBefore;
    private Double premiumAfter;
    private Double premiumDifference;
    private List<FieldChangeDto> changes;
}
