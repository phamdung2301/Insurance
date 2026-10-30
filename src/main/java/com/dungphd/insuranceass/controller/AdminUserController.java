package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AdminUserCreateRequest;
import com.dungphd.insuranceass.dto.request.SetEnabledRequest;
import com.dungphd.insuranceass.dto.request.UpdateRolesRequest;
import com.dungphd.insuranceass.dto.response.AdminAuditLogResponse;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/admin/users", "/api/admin/users"})
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.listUsers(), "Danh sách người dùng."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @Valid @RequestBody AdminUserCreateRequest request,
            Principal principal) {
        AdminUserResponse response = adminUserService.createUser(request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã tạo người dùng."));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @PathVariable String id,
            @Valid @RequestBody UpdateRolesRequest request,
            Principal principal) {
        AdminUserResponse response = adminUserService.updateRoles(id, request.getRoles(), actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật vai trò."));
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<AdminUserResponse>> setEnabled(
            @PathVariable String id,
            @Valid @RequestBody SetEnabledRequest request,
            Principal principal) {
        AdminUserResponse response = adminUserService.setEnabled(id, request.getEnabled(), actor(principal));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật trạng thái tài khoản."));
    }

    private String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
