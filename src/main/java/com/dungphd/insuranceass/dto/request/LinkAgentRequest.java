package com.dungphd.insuranceass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho POST /user/agent-link — gắn tài khoản khách hàng (một lần) với
 * đại lý whose link storefront đang được dùng (luồng 3: đăng ký qua link
 * đại lý). Idempotent: đã gắn rồi thì no-op, không lỗi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkAgentRequest {

    @NotBlank(message = "Thiếu slug đại lý.")
    @Size(max = 64, message = "Slug đại lý tối đa 64 ký tự.")
    private String agentSlug;
}
