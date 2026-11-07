package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyOtpRequest {

    @NotBlank(message = "Email là bắt buộc.")
    @Email(message = "Địa chỉ email không hợp lệ.")
    private String email;

    @NotBlank(message = "Mã OTP là bắt buộc.")
    private String otp;
}
