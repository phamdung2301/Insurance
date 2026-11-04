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
 * Bản ghi thanh toán qua cổng (SePay — chuyển khoản + webhook) — đợt payment.
 * Một policy QUOTED có thể có nhiều Payment (thanh toán lại sau khi
 * hủy/thất bại), nhưng tại một thời điểm chỉ tối đa một PENDING: khi tạo
 * payment mới, payment PENDING cũ của cùng policy bị chuyển CANCELLED
 * (supersede — xem PaymentServiceImpl.createPayment).
 *
 * <p>Số tiền {@code amount} được KHÓA tại lúc tạo payment (= totalPremium
 * lúc đó). Webhook luôn đối chiếu với amount đã lưu này, không đối chiếu với
 * totalPremium hiện tại của policy (giá có thể đã đổi sau đó).
 *
 * <p>SePay không có API tạo đơn: {@code transferContent} là nội dung chuyển
 * khoản duy nhất khách phải gõ (tra cứu ngược được payment từ content
 * webhook); {@code gatewayTransactionNo} tái dùng để lưu SePay transaction
 * {@code id} (dạng chuỗi) khi tiền về, phục vụ chống trùng webhook.
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

    /** Cổng thanh toán, hiện chỉ có SEPAY. */
    @Builder.Default
    private String provider = "SEPAY";

    /** Mã giao dịch riêng của hệ thống (dùng làm txnRef tra cứu). */
    @Indexed(unique = true)
    private String txnRef;

    /**
     * Nội dung chuyển khoản duy nhất khách phải gõ, dạng INS + 10 ký tự
     * [A-Z0-9]. Unique để tra cứu ngược payment từ content/code webhook.
     */
    @Indexed(unique = true)
    private String transferContent;

    @Builder.Default
    private GatewayPaymentStatus status = GatewayPaymentStatus.PENDING;

    /** SePay transaction {@code id} (dạng chuỗi), chỉ có sau khi webhook tiền về. */
    private String gatewayTransactionNo;

    /** Thời điểm webhook xác nhận tiền về (lấy từ transactionDate nếu parse được, nếu không là now). */
    private Instant paidAt;

    /** Email bên cầu đã tạo payment (principal lúc tạo). */
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    private Instant updatedAt;
}
