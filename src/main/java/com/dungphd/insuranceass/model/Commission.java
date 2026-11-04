package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Hoa hồng của agent trên một hợp đồng (đợt 3A).
 * Một policy tối đa một record (policyNumber unique) — xem tính idempotent
 * của {@code CommissionService#createForBound}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "commissions")
@CompoundIndex(name = "agentId_status_idx", def = "{'agentId': 1, 'status': 1}")
public class Commission {

    @Id
    private String id;

    /** Số hợp đồng sinh hoa hồng; unique — một policy chỉ có một commission. */
    @Indexed(unique = true)
    private String policyNumber;

    /** Email agent được hưởng (bản sao của Policy.assignedAgentEmail lúc BOUND). */
    @Indexed
    private String agentEmail;

    /**
     * Tenant hưởng hoa hồng — copy từ Policy.agentId lúc tạo commission (BOUND).
     * Nullable trong giai đoạn chuyển đổi; agentEmail giữ lại làm snapshot hiển thị.
     */
    @Indexed
    private String agentId;

    /** Số tiền hoa hồng = COMMISSION_RATE * Policy.totalPremium, làm tròn về đồng nguyên. */
    private Double amount;

    /** PENDING khi tạo, EARNED khi policy ACTIVE. */
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING;

    private Instant createdAt;

    /** Thời điểm chuyển sang EARNED; null khi còn PENDING. */
    private Instant earnedAt;
}
