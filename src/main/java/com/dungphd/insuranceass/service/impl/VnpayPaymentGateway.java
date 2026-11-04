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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * VNPay sandbox implementation của {@link PaymentGateway}.
 *
 * <p>Chuẩn ký VNPay (tài liệu sandbox): sắp xếp tham số theo key (a-z),
 * bỏ qua tham số rỗng, nối {@code key=URLEncoder(value)} bằng {@code &}
 * làm chuỗi hash, ký HMAC-SHA512 bằng HASH_SECRET, gắn kết quả vào
 * {@code vnp_SecureHash}. Verify làm ngược lại: bỏ {@code vnp_SecureHash} /
 * {@code vnp_SecureHashType} rồi ký lại và so sánh constant-time.
 *
 * <p>Config CHỈ từ env qua application.properties (không hardcode secret,
 * không hardcode domain prod):
 * {@code VNPAY_TMN_CODE, VNPAY_HASH_SECRET, VNPAY_URL (default sandbox),
 * VNPAY_RETURN_URL, VNPAY_IPN_URL}.
 * Thiếu credential → {@link #isConfigured()} false và
 * {@link #buildPaymentUrl} ném IllegalStateException với message rõ ràng;
 * app vẫn boot bình thường vì không có validate nào ở startup.
 */
@Slf4j
@Service
public class VnpayPaymentGateway implements PaymentGateway {

    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** VNPay tính giờ theo GMT+7. */
    private static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final String ipnUrl;

    public VnpayPaymentGateway(
            @Value("${app.vnpay.tmn-code:}") String tmnCode,
            @Value("${app.vnpay.hash-secret:}") String hashSecret,
            @Value("${app.vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${app.vnpay.return-url:}") String returnUrl,
            @Value("${app.vnpay.ipn-url:}") String ipnUrl) {
        this.tmnCode = tmnCode != null ? tmnCode.trim() : "";
        this.hashSecret = hashSecret != null ? hashSecret.trim() : "";
        this.payUrl = payUrl != null ? payUrl.trim() : "";
        this.returnUrl = returnUrl != null ? returnUrl.trim() : "";
        this.ipnUrl = ipnUrl != null ? ipnUrl.trim() : "";
    }

    @Override
    public boolean isConfigured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank();
    }

    @Override
    public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Cổng thanh toán VNPay chưa được cấu hình (thiếu VNPAY_TMN_CODE hoặc VNPAY_HASH_SECRET).");
        }
        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException("txnRef is required");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        // VNPay nhận amount theo đơn vị nhỏ: VND x 100.
        params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo != null ? orderInfo : ("Thanh toan " + txnRef));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        if (!returnUrl.isBlank()) {
            params.put("vnp_ReturnUrl", returnUrl);
        }
        params.put("vnp_IpAddr", clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1");
        ZonedDateTime now = ZonedDateTime.now(VN_OFFSET);
        params.put("vnp_CreateDate", now.format(VNPAY_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATE));

        String hashData = buildHashData(params);
        String secureHash = hmacSha512(hashSecret, hashData);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        query.append("&vnp_SecureHash=").append(secureHash);
        return payUrl + "?" + query;
    }

    @Override
    public VerificationResult verifySignature(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return VerificationResult.invalid("missing_params");
        }
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return VerificationResult.invalid("missing_signature");
        }
        if (!isConfigured()) {
            // Không có secret thì không thể xác thực — coi như verify fail,
            // không bao giờ coi IPN là hợp lệ khi thiếu cấu hình.
            return VerificationResult.invalid("gateway_not_configured");
        }
        Map<String, String> fields = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey();
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                fields.put(key, e.getValue());
            }
        }
        String expected = hmacSha512(hashSecret, buildHashData(fields));
        // So sánh constant-time, không log chữ ký hay secret.
        boolean match = MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        return match ? VerificationResult.ok() : VerificationResult.invalid("invalid_signature");
    }

    @Override
    public Long parseMinorAmountToVnd(String vnpAmount) {
        if (vnpAmount == null || vnpAmount.isBlank()) {
            return null;
        }
        try {
            long minor = Long.parseLong(vnpAmount.trim());
            if (minor < 0 || minor % 100 != 0) {
                return null;
            }
            return minor / 100;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Chuỗi hash: key=URLEncoder(value) nối bằng '&', key đã sort a-z (TreeMap). */
    private static String buildHashData(Map<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sortedParams.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute VNPay HMAC-SHA512", e);
        }
    }
}
