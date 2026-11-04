package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;

    /**
     * Tenant mà user này làm việc / được gán (Agent.id) — SaaS Phase 1, phương án C.
     * Nullable. Với ROLE_AGENT: tenant làm việc. Với ROLE_CUSTOMER: tenant được
     * gán (chỉ là gợi ý, nguồn sự thật của dữ liệu policy vẫn là Policy.agentId).
     * Với ADMIN/UNDERWRITER: để null và bỏ qua (không thuộc tenant nào).
     */
    @Indexed
    private String agentId;

    private String fullName;

    private String phone;

    private String address;

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private Boolean enabled = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
