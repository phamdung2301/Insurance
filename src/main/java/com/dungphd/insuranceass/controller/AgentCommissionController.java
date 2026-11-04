package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.AgentFunnelResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.CommissionListResponse;
import com.dungphd.insuranceass.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * Hoa hồng + phễu của agent (đợt 3A).
 * Cố tình KHÔNG thêm matcher riêng vào SecurityConfig (nằm ngoài phạm vi
 * đợt này): /agent/** chưa có matcher nào nên rơi vào
 * anyRequest().authenticated() cuối — chỉ yêu cầu đăng nhập ở tầng HTTP.
 * Scope theo role được enforce lại trong CommissionService (AGENT chỉ xem
 * của mình, ADMIN xem tất cả, role khác → ForbiddenException 403).
 * Controller chỉ lấy email từ JWT/Principal, không nhận email từ client.
 * Quy ước dual mapping như các controller khác trong repo.
 */
@RestController
@RequestMapping({"/agent/commissions", "/api/agent/commissions"})
@RequiredArgsConstructor
public class AgentCommissionController {

    private final CommissionService commissionService;

    /** GET /agent/commissions → {items, totalPending, totalEarned}. */
    @GetMapping
    public ApiResponse<CommissionListResponse> listCommissions(Principal principal) {
        return ApiResponse.success(
                commissionService.listForPrincipal(resolveEmail(principal)),
                "Danh sách hoa hồng.");
    }

    /** GET /agent/commissions/funnel → {agentEmail, draft, quoted, bound, active, total}. */
    @GetMapping("/funnel")
    public ApiResponse<AgentFunnelResponse> funnel(Principal principal) {
        return ApiResponse.success(
                commissionService.funnelForPrincipal(resolveEmail(principal)),
                "Phễu hợp đồng của đại lý.");
    }

    private String resolveEmail(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập");
    }
}
