package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AgentTenantUserCreateRequest;
import com.dungphd.insuranceass.dto.request.SetEnabledRequest;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.service.AgentTenantUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Quản lý user theo tenant cho ĐẠI LÝ — SaaS Phase 2 Track B mục 4 + Q8.
 *
 * <p>Matcher SecurityConfig {@code /agent/** → hasAnyRole("AGENT","ADMIN")}
 * cho cả ADMIN đi qua, nhưng service chặn ADMIN bằng 403 (ADMIN dùng
 * /admin/users riêng) — quyết này ghi rõ ở Track B mục 4, chọn 403 thay
 * vì redirect vì đây là API. Prefix /agent/users khớp mẫu route hiện có
 * của các controller agent khác (vd. /agent/policies/summary).</p>
 */
@RestController
@RequestMapping({"/agent/users", "/api/agent/users"})
@RequiredArgsConstructor
public class AgentTenantUserController {

    private final AgentTenantUserService agentTenantUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listTenantUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                agentTenantUserService.listTenantUsers(), "Danh sách người dùng của đại lý."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> createTenantUser(
            @Valid @RequestBody AgentTenantUserCreateRequest request,
            Principal principal) {
        AdminUserResponse response = agentTenantUserService.createTenantUser(request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã tạo tài khoản khách hàng cho đại lý."));
    }

    /**
     * PUT /agent/users/{id}/enabled — Q8: agent khóa/mở CUSTOMER thuộc
     * tenant mình (ghi audit). Xem guard đầy đủ ở
     * {@code AgentTenantUserServiceImpl.setTenantUserEnabled}.
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<AdminUserResponse>> setTenantUserEnabled(
            @PathVariable String id,
            @Valid @RequestBody SetEnabledRequest request,
            Principal principal) {
        AdminUserResponse response = agentTenantUserService.setTenantUserEnabled(
                id, request.getEnabled(), actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật trạng thái tài khoản."));
    }

    private String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
