package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectClaimRequest {

    @NotBlank(message = "Lý do từ chối bồi thường là bắt buộc.")
    @Size(max = 2000, message = "Lý do từ chối tối đa 2000 ký tự.")
    private String rejectReason;
}

