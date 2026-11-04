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

/**
 * Tenant record — một đại lý (SaaS Phase 1, phương án C, PLAN-SAAS §2.1).
 *
 * <p>{@code id} là tenantId nội bộ (Mongo sinh, String như {@code User.id} /
 * {@code Policy.id}); {@code code} chỉ để hiển thị/đối soát, do server sinh
 * (vd {@code AGT-0001}), KHÔNG dùng làm tenantId nội bộ. {@code contactEmail}
 * là email liên hệ, KHÔNG unique và KHÔNG dùng để scope — scope dùng
 * {@code Agent.id} qua {@code User.agentId} / {@code Policy.agentId}.</p>
 *
 * <p>Lưu ý về timestamp: codebase hiện không bật {@code @EnableMongoAuditing}
 * nên {@code @CreatedDate}/{@code @LastModifiedDate} ở đây chỉ là khai báo
 * theo mẫu {@code Policy}/{@code User}; {@code AgentService} tự set
 * {@code createdAt}/{@code updatedAt} tường minh khi tạo/cập nhật.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "agents")
public class Agent {

    /** TenantId, sinh bởi Mongo (String). */
    @Id
    private String id;

    /** Mã đại lý do server sinh, unique — vd AGT-0001. Client không được tự đặt. */
    @Indexed(unique = true)
    private String code;

    /** Tên đại lý hiển thị trên UI (bắt buộc). */
    private String displayName;

    /** Người liên hệ (tùy chọn). */
    private String contactName;

    /** Email liên hệ (không unique, khác với email đăng nhập). */
    private String contactEmail;

    private String phone;

    private String address;

    /** Trạng thái đại lý — Phase 1 chỉ lưu, chưa enforce (xem {@link AgentStatus}). */
    @Builder.Default
    @Indexed
    private AgentStatus status = AgentStatus.ACTIVE;

    /** Email ADMIN tạo đại lý (audit, theo pattern AdminAuditLog); "migration"/"seed" cho các luồng đó. */
    private String createdBy;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
