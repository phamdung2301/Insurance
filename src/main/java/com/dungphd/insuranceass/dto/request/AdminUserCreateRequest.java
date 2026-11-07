package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.dungphd.insuranceass.util.PasswordPolicy;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserCreateRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu là bắt buộc")
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.VIOLATION_MESSAGE)
    private String password;

    private String fullName;

    @NotEmpty(message = "roles không được rỗng")
    private List<String> roles;

    /**
     * Tenant (Agent.id) gán ngay lúc tạo (Track B mục 4, lấp gap Phase 1).
     * Nullable: để trống = chưa gán (ADMIN gán sau qua PUT /admin/users/{id}/agent).
     * Nếu gửi kèm thì Agent phải tồn tại, nếu không service trả 400.
     * Theo PLAN §3.5, tạo user ROLE_AGENT mà không gửi agentId cũng bị chặn
     * 400 — ADMIN phải tạo/chọn Agent trước, không tự sinh tenant ẩn.
     */
    private String agentId;
}
