package com.dungphd.insuranceass.service;

/**
 * Abstraction cổng thanh toán: business logic chỉ phụ thuộc interface này,
 * đổi cổng sau này không sửa PaymentService.
 *
 * <p>Impl hiện tại là SePay: SePay KHÔNG có API tạo đơn — backend tự sinh
 * nội dung chuyển khoản (transferContent) + QR VietQR, còn SePay chỉ báo
 * "có tiền vào" qua webhook. Vì vậy interface này không còn buildPaymentUrl
 * kiểu redirect như cổng cũ, mà là: sinh mã chuyển khoản, build QR, và
 * verify webhook HMAC-SHA256 trên RAW BODY.
 */
public interface PaymentGateway {

    /** Kết quả verify webhook: hợp lệ hay không + lý do. */
    record VerificationResult(boolean valid, String reason) {
        public static VerificationResult ok() {
            return new VerificationResult(true, "OK");
        }

        public static VerificationResult invalid(String reason) {
            return new VerificationResult(false, reason);
        }
    }

    /** true khi đã có đủ cấu hình để tạo payment (bankCode + accountNumber). */
    boolean isConfigured();

    /**
     * Sinh nội dung chuyển khoản duy nhất cho một payment, dạng
     * {@code INS} + 10 ký tự [A-Z0-9] (chữ/số không dấu, khớp được trong
     * nội dung khách gõ). Tính duy nhất tuyệt đối do service kiểm tra
     * qua repository + unique index trước khi lưu.
     */
    String generateTransferContent();

    /**
     * Build QR VietQR cho khách quét chuyển khoản.
     * Format: {@code https://vietqr.app/img?acc={acc}&bank={bank}&amount={amount}&des={content}&template=compact}
     * (+ {@code holder} khi có accountName cấu hình).
     *
     * @throws IllegalStateException khi cổng chưa được cấu hình (thiếu bank/acc)
     */
    String buildQrUrl(long amountVnd, String transferContent);

    /**
     * Verify webhook SePay bằng HMAC-SHA256 DUY NHẤT (không API Key/OAuth2/no-auth).
     * Chuỗi ký: {@code {timestamp}.{raw_body}} (RAW BODY gốc, không serialize lại),
     * header {@code X-SePay-Signature: sha256={hex}} + {@code X-SePay-Timestamp}
     * (unix giây). Verify fail khi: thiếu secret cấu hình (fail-closed),
     * thiếu/sai chữ ký, timestamp không parse được hoặc lệch quá ±300s
     * (chống replay). So sánh constant-time, không bao giờ log secret.
     * Không throw cho chữ ký sai — trả {@link VerificationResult} để caller
     * quyết định HTTP status (401 cho lỗi xác thực).
     */
    VerificationResult verifyWebhookSignature(String timestamp, byte[] rawBody, String signatureHeader);

    /**
     * Kiểm tra transferContent có khớp webhook không: khớp field {@code code}
     * của SePay (bằng đúng, không phân biệt hoa/thường), HOẶC transferContent
     * nằm trong content webhook sau khi uppercase + bỏ khoảng trắng (khách
     * có thể gõ thêm chữ, vd "SEVN63DC8E5C chuyen tien").
     */
    boolean contentMatches(String transferContent, String webhookContent, String sepayCode);

    /** Mã ngân hàng cấu hình (code/bin/short_name/alias, vd VCB). */
    String getBankCode();

    /** Số tài khoản nhận tiền cấu hình. */
    String getAccountNumber();

    /** Tên chủ tài khoản cấu hình (có thể rỗng → QR không kèm holder). */
    String getAccountName();
}
