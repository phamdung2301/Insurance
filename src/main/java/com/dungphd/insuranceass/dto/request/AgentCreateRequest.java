package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tạo đại lý (ADMIN-only, POST /admin/agents). Client KHÔNG được tự đặt
 * {@code code} (server sinh, tránh trùng) và không chọn {@code status} lúc tạo
 * (luôn ACTIVE; đổi trạng thái qua PUT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentCreateRequest {

    @NotBlank(message = "Tên đại lý là bắt buộc")
    @Size(max = 200, message = "Tên đại lý không vượt quá 200 ký tự")
    private String displayName;

    @Size(max = 200, message = "Tên liên hệ không vượt quá 200 ký tự")
    private String contactName;

    @Size(max = 200, message = "Email liên hệ không vượt quá 200 ký tự")
    private String contactEmail;

    @Size(max = 50, message = "Số điện thoại không vượt quá 50 ký tự")
    private String phone;

    @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
    private String address;
}
