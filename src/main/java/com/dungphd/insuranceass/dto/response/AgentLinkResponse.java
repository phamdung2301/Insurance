package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả gắn tài khoản khách với đại lý (POST /user/agent-link).
 * linked=false nghĩa là tài khoản đã gắn đại lý từ trước — no-op an toàn
 * để FE gọi sau mỗi lần đăng nhập mà không gây lỗi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentLinkResponse {
    private boolean linked;
    private String agentName;
    private String agentSlug;
}
