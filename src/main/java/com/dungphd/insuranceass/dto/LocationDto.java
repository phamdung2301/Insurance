package com.dungphd.insuranceass.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationDto {
    @NotNull(message = "Location ID is required")
    private Integer locationId;

    @NotBlank(message = "Address is required")
    private String address;

    @Valid
    @Builder.Default
    private List<CoverageDto> coverages = new ArrayList<>();
}
