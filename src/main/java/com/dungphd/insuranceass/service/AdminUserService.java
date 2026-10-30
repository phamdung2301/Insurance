package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AdminUserCreateRequest;
import com.dungphd.insuranceass.dto.response.AdminAuditLogResponse;
import com.dungphd.insuranceass.dto.response.AdminUserResponse;

import java.util.List;

public interface AdminUserService {

    List<AdminUserResponse> listUsers();

    AdminUserResponse createUser(AdminUserCreateRequest request, String actor);

    AdminUserResponse updateRoles(String userId, List<String> roles, String actor);

    AdminUserResponse setEnabled(String userId, boolean enabled, String actor);

    List<AdminAuditLogResponse> listAuditLogs();
}
