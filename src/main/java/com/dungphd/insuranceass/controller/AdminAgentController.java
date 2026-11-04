package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * CRUD tối thiểu cho Agent — SaaS Phase 1. ADMIN-only qua matcher /admin/**
 * (và /api/admin/**) đã có sẵn trong SecurityConfig, không cần matcher mới.
 * KHÔNG có endpoint xóa: đóng đại lý bằng PUT status = CLOSED.
 * KHÔNG làm FE ở Phase 1 (UI quản trị agent là Phase 3).
 */
@RestController
@RequestMapping({"/admin/agents", "/api/admin/agents"})
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentService agentService;

    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> createAgent(
            @Valid @RequestBody AgentCreateRequest request,
            Principal principal) {
        AgentResponse response = agentService.createAgent(request, actor(principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã tạo đại lý."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentResponse>>> listAgents(
            @RequestParam(required = false) AgentStatus status) {
        return ResponseEntity.ok(ApiResponse.success(agentService.listAgents(status), "Danh sách đại lý."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgent(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(agentService.getAgent(id), "Chi tiết đại lý."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> updateAgent(
            @PathVariable String id,
            @Valid @RequestBody AgentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(agentService.updateAgent(id, request), "Đã cập nhật đại lý."));
    }

    private String actor(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
