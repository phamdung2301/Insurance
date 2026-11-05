package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho POST /agent/users (Track B mục 4).
 *
 * <p>CỐ TÌNH không có field roles: server luôn ép {@code ROLE_CUSTOMER}
 * duy nhất + tenant từ TenantContext của caller. Agent không thể tạo
 * AGENT/UW/ADMIN qua endpoint này bằng bất kỳ cách nào (không có field
 * để gửi, không có đường đổi role sau đó — endpoint này cũng không có
 * API đổi role).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTenantUserCreateRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Password là bắt buộc")
    private String password;

    private String fullName;
}
