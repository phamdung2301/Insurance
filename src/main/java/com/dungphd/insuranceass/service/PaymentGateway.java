package com.dungphd.insuranceass.service;

import java.util.Map;

/**
 * Abstraction cổng thanh toán (ANALYSIS §5 quyết định 1): business logic
 * chỉ phụ thuộc interface này, đổi cổng sau này không sửa PaymentService.
 */
public interface PaymentGateway {

    /** Kết quả verify tham số IPN/return: hợp lệ hay không + lý do. */
    record VerificationResult(boolean valid, String reason) {
        public static VerificationResult ok() {
            return new VerificationResult(true, "OK");
        }

        public static VerificationResult invalid(String reason) {
            return new VerificationResult(false, reason);
        }
    }

    /** true khi đã có đủ credential để tạo payment (tmnCode + hashSecret). */
    boolean isConfigured();

    /**
     * Tạo URL thanh toán cho FE redirect khách sang cổng.
     *
     * @param txnRef     mã giao dịch riêng của hệ thống (vnp_TxnRef)
     * @param amountVnd  số tiền VND (số nguyên; VNPay nhận amount x100 ở tầng impl)
     * @param orderInfo  mô tả đơn hàng (vnp_OrderInfo)
     * @param clientIp   IP trình duyệt khách (vnp_IpAddr)
     * @throws IllegalStateException khi cổng chưa được cấu hình (thiếu credential)
     */
    String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp);

    /**
     * Verify chữ ký của tham số VNPay gửi về (IPN hoặc return URL).
     * Không throw cho chữ ký sai — trả về {@link VerificationResult} để
     * caller quyết định mã RspCode/HTTP status, và không bao giờ log secret.
     */
    VerificationResult verifySignature(Map<String, String> params);

    /**
     * Đổi vnp_Amount (đơn vị nhỏ = VND x100) sang VND.
     * Trả null khi không parse được số.
     */
    Long parseMinorAmountToVnd(String vnpAmount);
}
