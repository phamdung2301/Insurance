package com.dungphd.insuranceass.dto;

import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationDto {

    private Integer locationId;

    @NotBlank(message = "Address is required")
    private String address;

    @Valid
    @Builder.Default
    private List<CoverageDto> coverages = new ArrayList<>();

    public static LocationDto fromEntity(Location location) {
        if (location == null) return null;
        List<CoverageDto> coverageDtos = location.getCoverages() != null
                ? location.getCoverages().stream().map(CoverageDto::fromEntity).collect(Collectors.toList())
                : new ArrayList<>();

        return LocationDto.builder()
                .locationId(location.getLocationId())
                .address(location.getAddress())
                .coverages(coverageDtos)
                .build();
    }

    public Location toEntity() {
        List<Coverage> coverageEntities = coverages != null
                ? coverages.stream().map(CoverageDto::toEntity).collect(Collectors.toList())
                : new ArrayList<>();

        return Location.builder()
                .locationId(locationId)
                .address(address)
                .coverages(coverageEntities)
                .build();
    }
}
