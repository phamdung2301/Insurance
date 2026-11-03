package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hồ sơ yêu cầu bồi thường hợp đồng bảo hiểm (Claim).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "claims")
@CompoundIndex(name = "policy_status_idx", def = "{'policyNumber': 1, 'status': 1}")
public class Claim {

    @Id
    private String id;

    @Indexed
    private String policyNumber;

    @Indexed
    private String claimantEmail;

    /** Mô tả chi tiết thiệt hại (bắt buộc, tối đa 5000 ký tự) */
    private String damageDescription;

    /** Số tiền yêu cầu bồi thường (> 0) */
    private Double claimedAmount;

    /** Số tiền duyệt bồi thường (nullable, <= claimedAmount) */
    private Double approvedAmount;

    @Indexed
    private ClaimStatus status;

    /** Lý do từ chối (bắt buộc khi REJECTED) */
    private String rejectReason;

    /** Ghi chú xử lý của Quản trị viên (ADMIN) */
    private String handlerNote;

    @Builder.Default
    private List<ClaimPhoto> photos = new ArrayList<>();

    @Builder.Default
    private List<ClaimStatusHistory> statusHistory = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

