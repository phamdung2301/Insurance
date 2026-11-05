package com.dungphd.insuranceass.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho PUT /admin/users/{id}/agent (Track B mục 4).
 * {@code agentId} null/rỗng = thu hồi tenant (về chưa gán); khác null thì
 * Agent phải tồn tại. Việc gán/đổi tenant của user KHÔNG tự đổi tenant
 * của các policy cũ của user đó — nguồn sự thật của dữ liệu policy vẫn
 * là {@code Policy.agentId}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetAgentRequest {

    private String agentId;
}
