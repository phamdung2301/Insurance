package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {
    private String id;
    private String email;
    private String fullName;
    private List<String> roles;
    /** Tenant (Agent.id) user thuộc về — null nếu chưa gán / ADMIN-UW (Track B mục 4). */
    private String agentId;
    private Boolean enabled;
    private Instant createdAt;
}
