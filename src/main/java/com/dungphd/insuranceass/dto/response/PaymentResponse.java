package com.dungphd.insuranceass.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response cho payment qua cổng SePay. Các field QR/tài khoản
 * ({@code qrUrl}, {@code bankCode}, {@code accountNumber},
 * {@code accountName}) chỉ có ở response tạo payment (FE hiển thị QR +
 * thông tin chuyển khoản); các response tra cứu/lịch sử không trả chúng
 * (NON_NULL loại bỏ). {@code transferContent} có ở response tạo payment
 * và lịch sử (để đối soát); {@code policyStatus} chỉ có ở response tra
 * cứu trạng thái.
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
    private String transferContent;
    private String status;
    private String gatewayTransactionNo;
    private Instant paidAt;
    private Instant createdAt;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String qrUrl;
    private String policyStatus;
}
