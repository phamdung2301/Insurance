package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateClaimRequest {

    @NotBlank(message = "Số hợp đồng bảo hiểm là bắt buộc.")
    private String policyNumber;

    @NotNull(message = "Số tiền yêu cầu bồi thường là bắt buộc.")
    @Positive(message = "Số tiền yêu cầu bồi thường phải lớn hơn 0.")
    @DecimalMax(value = "10000000000", message = "Số tiền yêu cầu bồi thường không được vượt quá 10 tỷ VND.")
    private Double claimedAmount;

    @NotBlank(message = "Mô tả chi tiết thiệt hại là bắt buộc.")
    @Size(max = 5000, message = "Mô tả thiệt hại tối đa 5000 ký tự.")
    private String damageDescription;
}

