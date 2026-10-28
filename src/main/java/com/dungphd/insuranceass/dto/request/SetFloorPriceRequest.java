package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetFloorPriceRequest {

    @NotNull(message = "Floor price is required")
    @Positive(message = "Floor price must be greater than 0")
    private BigDecimal floorPrice;
}
