package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
import com.dungphd.insuranceass.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolve slug public cho storefront đại lý (SaaS Phase 2 Track C).
 *
 * <p>Endpoint CÔNG KHAI (permitAll trong SecurityConfig, chỉ GET path này —
 * không mở rộng sang /agents/** khác): khách chưa đăng nhập vẫn xem được
 * storefront. Chỉ trả field an toàn để hiển thị (xem
 * {@code PublicAgentResponse}) — không id nội bộ, không code.</p>
 *
 * <p>Mã lỗi phân biệt cho FE (dùng nhất quán cả ở POST /policies kèm
 * agentSlug): slug không tồn tại → 404; slug của đại lý SUSPENDED/CLOSED →
 * 410 Gone (link chết, FE hiện trang exception riêng); ACTIVE → 200.</p>
 */
@RestController
@RequestMapping({"/agents/public", "/api/agents/public"})
@RequiredArgsConstructor
public class PublicAgentController {

    private final AgentService agentService;

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<PublicAgentResponse>> resolveBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.resolvePublicBySlug(slug), "Thông tin đại lý."));
    }
}
