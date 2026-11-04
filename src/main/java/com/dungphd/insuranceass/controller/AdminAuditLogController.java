package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.AdminAuditLogResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/admin/audit-logs", "/api/admin/audit-logs"})
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAuditLogResponse>>> listAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.listAuditLogs(), "Danh sách nhật ký quản trị."));
    }
}
