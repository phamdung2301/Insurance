package com.dungphd.insuranceass.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
}
