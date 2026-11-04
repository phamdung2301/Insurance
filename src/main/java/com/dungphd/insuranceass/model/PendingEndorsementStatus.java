package com.dungphd.insuranceass.model;

/** Trạng thái maker-checker của một endorsement chờ duyệt (đợt 4B). */
public enum PendingEndorsementStatus {
    PENDING,
    /**
     * Track 2 — trạng thái lease thoáng qua, CHỈ do CAS claim đặt
     * ({@code PendingEndorsementApproval.claimForApproval}): checker thắng
     * giữ APPROVING trong lúc apply; thắng → APPROVED (trong cùng
     * transaction với apply), thua apply → trả về PENDING để duyệt lại.
     * APPROVING không bao giờ là trạng thái cuối và không hiện trong hàng
     * đợi (list chỉ query PENDING). Nếu JVM crash đúng giữa claim và
     * apply, bản ghi kẹt ở APPROVING và cần operator reset tay về PENDING
     * (xem docs/TRANSACTION-RUNBOOK.md) — không có sweeper tự động.
     */
    APPROVING,
    APPROVED,
    REJECTED
}
