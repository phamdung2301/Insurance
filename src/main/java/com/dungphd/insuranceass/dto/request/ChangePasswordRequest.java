package com.dungphd.insuranceass.dto.request;

import com.dungphd.insuranceass.util.PasswordPolicy;
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
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    private String oldPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 8, max = 50, message = "Mật khẩu mới phải có độ dài 8–50 ký tự.")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.VIOLATION_MESSAGE)
    private String newPassword;

    private String confirmPassword;
}
