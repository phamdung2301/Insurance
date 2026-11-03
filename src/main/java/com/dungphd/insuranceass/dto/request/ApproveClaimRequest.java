package com.dungphd.insuranceass.dto.request;

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
public class ApproveClaimRequest {

    @NotNull(message = "Số tiền duyệt bồi thường là bắt buộc.")
    @Positive(message = "Số tiền duyệt phải lớn hơn 0.")
    private Double approvedAmount;

    @Size(max = 2000, message = "Ghi chú duyệt tối đa 2000 ký tự.")
    private String handlerNote;
}

