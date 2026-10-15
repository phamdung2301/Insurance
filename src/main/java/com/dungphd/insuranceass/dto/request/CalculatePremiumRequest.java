package com.dungphd.insuranceass.dto.request;

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
public class CalculatePremiumRequest {

    @NotBlank(message = "Mã gói bảo hiểm không được để trống")
    private String coverageCode;

    @NotNull(message = "Hạn mức bảo hiểm không được để trống")
    @Min(value = 1000000, message = "Hạn mức bảo hiểm tối thiểu là 1.000.000 VND")
    private Double limit;

    private Double deductible;

    @Min(value = 1, message = "Thời hạn bảo hiểm tối thiểu là 1 tháng")
    private Integer termMonths;

    private Double riskFactor;
}
