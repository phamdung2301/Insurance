package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.service.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * SePay implementation của {@link PaymentGateway}.
 *
 * <p>SePay KHÔNG có API tạo đơn: backend tự sinh transferContent + QR
 * VietQR, SePay chỉ gửi webhook "Có tiền vào" (In_only) về URL cấu hình
 * tại my.sepay.vn. Webhook xác thực DUY NHẤT bằng HMAC-SHA256 theo tài
 * liệu SePay: header {@code X-SePay-Signature: sha256={hex}} +
 * {@code X-SePay-Timestamp}, chuỗi ký {@code {timestamp}.{raw_body}}.
 *
 * <p>Config CHỈ từ env qua application.properties (không hardcode secret):
 * {@code SEPAY_WEBHOOK_SECRET, SEPAY_BANK_CODE, SEPAY_ACCOUNT_NUMBER,
 * SEPAY_ACCOUNT_NAME, SEPAY_STORE_NAME}. Thiếu bank/acc → isConfigured()
 * false, tạo payment trả 503 rõ ràng; thiếu secret → webhook fail-closed
 * 401. App vẫn boot bình thường vì không có validate nào ở startup.
 */
@Slf4j
@Service
public class SepayPaymentGateway implements PaymentGateway {

    /** Chênh lệch timestamp cho phép: ±300s (chống replay). */
    static final long TIMESTAMP_TOLERANCE_SECONDS = 300L;

    private static final String TRANSFER_CONTENT_PREFIX = "INS";
    private static final int TRANSFER_CONTENT_RANDOM_LENGTH = 10;
    private static final String TRANSFER_CONTENT_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String webhookSecret;
    private final String bankCode;
    private final String accountNumber;
    private final String accountName;

    public SepayPaymentGateway(
            @Value("${app.sepay.webhook-secret:}") String webhookSecret,
            @Value("${app.sepay.bank-code:}") String bankCode,
            @Value("${app.sepay.account-number:}") String accountNumber,
            @Value("${app.sepay.account-name:}") String accountName,
            @Value("${app.sepay.store-name:}") String storeName) {
        // storeName giữ trong constructor để rõ contract config (hiện chưa
        // dùng để build QR — QR chỉ cần acc/bank/amount/des/holder), tránh
        // lặng lẽ bỏ một property đã khai báo trong application.properties.
        if (storeName != null && !storeName.isBlank()) {
            log.debug("SePay store name configured (not used in QR payload)");
        }
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
        this.bankCode = bankCode != null ? bankCode.trim() : "";
        this.accountNumber = accountNumber != null ? accountNumber.trim() : "";
        this.accountName = accountName != null ? accountName.trim() : "";
    }

    @Override
    public boolean isConfigured() {
        return !bankCode.isBlank() && !accountNumber.isBlank();
    }

    @Override
    public String generateTransferContent() {
        StringBuilder sb = new StringBuilder(TRANSFER_CONTENT_PREFIX);
        for (int i = 0; i < TRANSFER_CONTENT_RANDOM_LENGTH; i++) {
            sb.append(TRANSFER_CONTENT_ALPHABET.charAt(RANDOM.nextInt(TRANSFER_CONTENT_ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    public String buildQrUrl(long amountVnd, String transferContent) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Cổng thanh toán SePay chưa được cấu hình (thiếu SEPAY_BANK_CODE hoặc SEPAY_ACCOUNT_NUMBER).");
        }
        if (transferContent == null || transferContent.isBlank()) {
            throw new IllegalArgumentException("transferContent is required");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        StringBuilder url = new StringBuilder("https://vietqr.app/img?acc=")
                .append(encode(accountNumber))
                .append("&bank=").append(encode(bankCode))
                .append("&amount=").append(amountVnd)
                .append("&des=").append(encode(transferContent.trim()))
                .append("&template=compact");
        if (!accountName.isBlank()) {
            url.append("&holder=").append(encode(accountName));
        }
        return url.toString();
    }

    @Override
    public VerificationResult verifyWebhookSignature(String timestamp, byte[] rawBody, String signatureHeader) {
        if (webhookSecret.isBlank()) {
            // Fail-closed: không có secret thì không thể xác thực — không bao
            // giờ coi webhook là hợp lệ khi thiếu cấu hình.
            return VerificationResult.invalid("gateway_not_configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()
                || !signatureHeader.trim().toLowerCase().startsWith("sha256=")) {
            return VerificationResult.invalid("missing_signature");
        }
        String receivedHex = signatureHeader.trim().substring("sha256=".length()).trim();
        if (receivedHex.isBlank()) {
            return VerificationResult.invalid("missing_signature");
        }
        if (timestamp == null || timestamp.isBlank()) {
            return VerificationResult.invalid("invalid_timestamp");
        }
        String ts = timestamp.trim();
        long tsSeconds;
        try {
            tsSeconds = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return VerificationResult.invalid("invalid_timestamp");
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSeconds - tsSeconds) > TIMESTAMP_TOLERANCE_SECONDS) {
            return VerificationResult.invalid("timestamp_expired");
        }
        if (rawBody == null) {
            return VerificationResult.invalid("missing_body");
        }
        // Ký trên RAW BODY gốc: {timestamp}.{raw_body}. Không parse JSON rồi
        // serialize lại — bytes khác là chữ ký khác.
        String signedData = ts + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expectedHex = hmacSha256Hex(webhookSecret, signedData);
        // So sánh constant-time, không log chữ ký hay secret.
        boolean match = MessageDigest.isEqual(
                expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8),
                receivedHex.toLowerCase().getBytes(StandardCharsets.UTF_8));
        return match ? VerificationResult.ok() : VerificationResult.invalid("invalid_signature");
    }

    @Override
    public boolean contentMatches(String transferContent, String webhookContent, String sepayCode) {
        if (transferContent == null || transferContent.isBlank()) {
            return false;
        }
        String target = transferContent.trim().toUpperCase();
        if (sepayCode != null && target.equals(sepayCode.trim().toUpperCase())) {
            return true;
        }
        if (webhookContent == null) {
            return false;
        }
        // Uppercase + bỏ khoảng trắng rồi kiểm tra "nằm trong" (khách gõ thêm chữ).
        String normalized = webhookContent.toUpperCase().replaceAll("\\s+", "");
        return normalized.contains(target);
    }

    @Override
    public String getBankCode() {
        return bankCode;
    }

    @Override
    public String getAccountNumber() {
        return accountNumber;
    }

    @Override
    public String getAccountName() {
        return accountName;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SePay HMAC-SHA256", e);
        }
    }
}
