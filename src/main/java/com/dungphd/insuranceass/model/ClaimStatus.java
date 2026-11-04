package com.dungphd.insuranceass.model;

/**
 * Trạng thái của một yêu cầu bồi thường hợp đồng:
 * SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID
 *               UNDER_REVIEW -> REJECTED
 * REJECTED và PAID là trạng thái cuối.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    PAID
}

