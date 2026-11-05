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

    /**
     * Slug public trong link {@code /a/<slug>} (SaaS Phase 2 Track C, ADMIN
     * đặt, tùy chọn lúc tạo — bổ sung sau qua PUT cũng được). Service chuẩn
     * hóa về chữ thường và validate pattern {@code [a-z0-9-]} (xem
     * {@code AgentSlug}); trùng slug → 400. KHÔNG dùng {@code code} làm slug.
     */
    @Size(max = 64, message = "Slug không vượt quá 64 ký tự")
    private String slug;
}
