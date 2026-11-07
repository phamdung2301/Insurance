package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndorsementRequest {

    private String policyNumber;

    @NotBlank(message = "Lý do điều chỉnh hợp đồng là bắt buộc.")
    private String reason;

    private String description;

    @Valid
    private InsuredDto insured;

    @Valid
    private List<LocationDto> locations;

    private Instant effectiveDate;

    private Instant expirationDate;
}
