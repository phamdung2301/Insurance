package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetFloorPriceRequest {

    @NotNull(message = "floorPremium là bắt buộc.")
    @Positive(message = "floorPremium phải lớn hơn 0.")
    private Double floorPremium;
}

