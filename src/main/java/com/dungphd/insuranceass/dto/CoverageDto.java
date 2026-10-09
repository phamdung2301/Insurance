package com.dungphd.insuranceass.dto;

import com.dungphd.insuranceass.model.Coverage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageDto {

    @NotBlank(message = "Coverage code is required")
    private String coverageCode;

    private String coverageName;

    // STANDARD | ENHANCED | COMPREHENSIVE
    private String coverageType;

    @NotNull(message = "Coverage limit is required")
    @Min(value = 0, message = "Limit must be non-negative")
    private Double limit;

    @Min(value = 0, message = "Deductible must be non-negative")
    private Double deductible;

    // Term in months: 12, 24, 36. Defaults to 12 if not provided.
    private Integer termMonths;

    // Base rate % from pricing matrix (e.g. 0.075). If null, will be auto-looked-up.
    private Double baseRate;

    // Final computed premium. If null, backend will auto-calculate.
    private Double premium;

    public static CoverageDto fromEntity(Coverage coverage) {
        if (coverage == null) return null;
        return CoverageDto.builder()
                .coverageCode(coverage.getCoverageCode())
                .coverageName(coverage.getCoverageName())
                .coverageType(coverage.getCoverageType())
                .limit(coverage.getLimit())
                .deductible(coverage.getDeductible())
                .termMonths(coverage.getTermMonths())
                .baseRate(coverage.getBaseRate())
                .premium(coverage.getPremium())
                .build();
    }

    public Coverage toEntity() {
        return Coverage.builder()
                .coverageCode(coverageCode)
                .coverageName(coverageName)
                .coverageType(coverageType)
                .limit(limit)
                .deductible(deductible)
                .termMonths(termMonths != null ? termMonths : 12)
                .baseRate(baseRate)
                .premium(premium != null ? premium : 0.0)
                .build();
    }
}
