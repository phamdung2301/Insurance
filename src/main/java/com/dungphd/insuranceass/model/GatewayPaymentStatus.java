package com.dungphd.insuranceass.model;

/**
 * Trạng thái của một bản ghi thanh toán qua cổng (collection "payments").
 * Khác với {@link PaymentStatus} (UNCLAIMED/CLAIMED/CONFIRMED) của luồng
 * thủ công "Tôi đã chuyển khoản" — hai luồng không dùng chung trạng thái
 * để tránh làm nhiễu marker đối soát thủ công (DESIGN-BRIEF §1.2, §7).
 */
public enum GatewayPaymentStatus {
    /** Đã tạo payment, đang chờ VNPay gọi IPN. */
    PENDING,
    /** IPN verify thành công, tiền đã về, policy đã sang ACTIVE. */
    PAID,
    /** VNPay báo giao dịch thất bại (response code != "00"). */
    FAILED,
    /** Bị supersede bởi một payment mới của cùng policy, hoặc khách hủy. */
    CANCELLED
}
