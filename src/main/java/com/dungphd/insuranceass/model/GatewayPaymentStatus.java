package com.dungphd.insuranceass.model;

/**
 * Trạng thái của một bản ghi thanh toán qua cổng (collection "payments").
 * Khác với {@link PaymentStatus} (UNCLAIMED/CLAIMED/CONFIRMED) của luồng
 * thủ công "Tôi đã chuyển khoản" — hai luồng không dùng chung trạng thái
 * để tránh làm nhiễu marker đối soát thủ công (DESIGN-BRIEF §1.2, §7).
 */
public enum GatewayPaymentStatus {
    /** Đã tạo payment, đang chờ webhook SePay báo tiền vào. */
    PENDING,
    /** Webhook verify thành công, tiền đã về, policy đã sang ACTIVE. */
    PAID,
    /** Giao dịch thất bại/hủy (vd bị supersede bởi payment mới). */
    FAILED,
    /** Bị supersede bởi một payment mới của cùng policy, hoặc khách hủy. */
    CANCELLED
}
