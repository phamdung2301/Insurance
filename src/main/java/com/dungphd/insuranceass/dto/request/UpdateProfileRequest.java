package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Họ tên phải có độ dài 2–100 ký tự.")
    private String fullName;

    @Pattern(regexp = "^$|^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không hợp lệ (vd: 0912345678).")
    private String phone;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
    private String address;
}
