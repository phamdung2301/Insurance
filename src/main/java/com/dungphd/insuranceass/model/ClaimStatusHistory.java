package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lịch sử chuyển trạng thái yêu cầu bồi thường (append-only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimStatusHistory {

    private String id;
    private ClaimStatus fromStatus;
    private ClaimStatus toStatus;
    private String changedBy;
    private Instant changedAt;
    private String note;
}

