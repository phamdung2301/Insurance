package com.dungphd.insuranceass.model;

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
 * Bản ghi thanh toán qua cổng (VNPay sandbox) — MỚI, đợt payment.
 * Một policy QUOTED có thể có nhiều Payment (thanh toán lại sau khi
 * hủy/thất bại), nhưng tại một thời điểm chỉ tối đa một PENDING: khi tạo
 * payment mới, payment PENDING cũ của cùng policy bị chuyển CANCELLED
 * (supersede — xem PaymentServiceImpl.createPayment).
 *
 * <p>Số tiền {@code amount} được KHÓA tại lúc tạo payment (= totalPremium
 * lúc đó). IPN luôn đối chiếu với amount đã lưu này, không đối chiếu với
 * totalPremium hiện tại của policy (giá có thể đã đổi sau đó).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    private String policyNumber;

    /** Số tiền phải trả (VND), khóa tại lúc tạo payment. */
    private Double amount;

    /** Cổng thanh toán, hiện chỉ có VNPAY. */
    @Builder.Default
    private String provider = "VNPAY";

    /** Mã giao dịch riêng của hệ thống, gửi sang VNPay làm vnp_TxnRef. */
    @Indexed(unique = true)
    private String txnRef;

    @Builder.Default
    private GatewayPaymentStatus status = GatewayPaymentStatus.PENDING;

    /** Mã giao dịch phía VNPay (vnp_TransactionNo), chỉ có sau khi IPN thành công. */
    private String gatewayTransactionNo;

    /** Thời điểm IPN xác nhận tiền về (lấy từ vnp_PayDate nếu parse được, nếu không là now). */
    private Instant paidAt;

    /** Email bên cầu đã tạo payment (principal lúc tạo). */
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    private Instant updatedAt;
}
