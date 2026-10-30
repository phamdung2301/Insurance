package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Ghi chú nội bộ của UNDERWRITER về một hợp đồng (đợt 3C, việc 1).
 * Kênh riêng biệt với Policy/PolicyResponse: notes KHÔNG bao giờ được đưa
 * vào PolicyResponse hay mapper Policy → PolicyResponse, nên CUSTOMER
 * không thấy được ghi chú nội bộ này qua API hợp đồng.
 * Chỉ ROLE_UNDERWRITER và ROLE_ADMIN được đọc/ghi (enforce ở
 * PolicyNoteServiceImpl, fail-closed).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policy_notes")
public class PolicyNote {

    @Id
    private String id;

    private String policyNumber;

    /** Email của người viết — service tự set từ principal, client không gửi. */
    private String authorEmail;

    private String text;

    private Instant createdAt;
}
