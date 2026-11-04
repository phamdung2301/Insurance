package com.dungphd.insuranceass.model;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Endorsement chờ duyệt theo maker-checker (đợt 4B).
 *
 * {@code request} nhúng TRỰC TIẾP EndorsementRequest gốc (POJO Lombok thuần,
 * các DTO con đều có no-args constructor) nên Spring Data MongoDB persist
 * được thành sub-document mà không cần serialise JSON thủ công. Khi duyệt,
 * đúng object này được đưa lại vào luồng applyEndorsement hiện có.
 *
 * {@code premiumChangePct} là tỷ lệ tuyệt đối |phí mới - phí cũ| / phí cũ,
 * làm tròn 4 chữ số thập phân. Quy ước phí cũ = 0: phí mới != 0 thì lưu 1.0
 * (tức 100%, vượt ngưỡng) vì không chia được cho 0; phí mới cũng = 0 thì 0.0.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "pending_endorsements")
public class PendingEndorsement {

    @Id
    private String id;

    @Indexed
    private String policyNumber;

    /** Nội dung EndorsementRequest gốc, nhúng trực tiếp (xem javadoc class). */
    private EndorsementRequest request;

    private String requestedBy;

    private Instant requestedAt;

    /** Tỷ lệ đổi phí tuyệt đối, ví dụ 0.20 = 20%. */
    private Double premiumChangePct;

    /** Phí cũ / phí mới dự kiến tại thời điểm yêu cầu (tham khảo, debug). */
    private Double oldPremium;

    private Double projectedPremium;

    @Builder.Default
    private PendingEndorsementStatus status = PendingEndorsementStatus.PENDING;

    private String approvedBy;

    private Instant approvedAt;

    private String rejectedBy;

    private Instant rejectedAt;

    @CreatedDate
    private Instant createdAt;
}
