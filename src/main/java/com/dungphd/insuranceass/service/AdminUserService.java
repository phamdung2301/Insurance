package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AdminUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminAuditLogResponse;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;

import java.util.List;

public interface AdminUserService {

    List<AdminUserResponse> listUsers();

    /** Lọc theo tenant (Track B mục 4); agentId null/rỗng = toàn bộ (như listUsers()). */
    List<AdminUserResponse> listUsers(String agentId);

    AdminUserResponse createUser(AdminUserCreateRequest request, String actor);

    AdminUserResponse updateRoles(String userId, List<String> roles, String actor);

    AdminUserResponse setEnabled(String userId, boolean enabled, String actor);

    /**
     * Gán/đổi/thu hồi tenant cho user (Track B mục 4, ADMIN-only).
     * agentId null/rỗng = thu hồi về chưa gán. Chỉ đổi {@code User.agentId};
     * KHÔNG tự đổi {@code Policy.agentId} của các policy cũ của user đó
     * (nguồn sự thật tenant của policy là Policy.agentId — PLAN §3.4).
     * Mọi lần gọi đều ghi AdminAuditLog action ASSIGN_TENANT.
     */
    AdminUserResponse setAgent(String userId, String agentId, String actor);

    List<AdminAuditLogResponse> listAuditLogs();
}
