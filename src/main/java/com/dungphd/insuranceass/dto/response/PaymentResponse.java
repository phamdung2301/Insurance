package com.dungphd.insuranceass.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response cho payment qua cổng. Field {@code paymentUrl} chỉ có ở
 * response tạo payment (FE dùng để redirect sang VNPay); các response
 * tra cứu/lịch sử không trả URL này (NON_NULL loại bỏ).
 * {@code policyStatus} chỉ có ở response tra cứu return URL.
 * {@code signatureValid}: true khi FE gửi kèm vnp_SecureHash và verify
 * pass; false khi FE chỉ gửi txnRef (không có chữ ký để verify).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private String id;
    private String policyNumber;
    private Double amount;
    private String provider;
    private String txnRef;
    private String status;
    private String gatewayTransactionNo;
    private Instant paidAt;
    private Instant createdAt;
    private String paymentUrl;
    private String policyStatus;
    private Boolean signatureValid;
}
