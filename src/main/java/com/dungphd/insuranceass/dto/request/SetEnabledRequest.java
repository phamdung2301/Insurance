package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetEnabledRequest {

    @NotNull(message = "enabled là bắt buộc")
    private Boolean enabled;
}
