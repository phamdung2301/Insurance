package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.service.impl.SepayPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SepayPaymentGatewayTest {

    private static final String SECRET = "TESTSEPAYSECRET1234567890";
    private static final String BANK = "VCB";
    private static final String ACC = "1234567890";
    private static final String HOLDER = "NGUYEN VAN A";

    private SepayPaymentGateway gateway() {
        return new SepayPaymentGateway(SECRET, BANK, ACC, HOLDER, "Test Store");
    }

    /** Ký đúng chuẩn SePay: HMAC-SHA256("{timestamp}.{raw_body}", secret), hex. */
    private static String sign(String secret, String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
            return "sha256=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nowTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    @Test
    void buildQrUrl_formatMatchesSpec_withHolder() {
        String url = gateway().buildQrUrl(5_000_000L, "INSABCDEF1234");
        assertEquals("https://vietqr.app/img?acc=1234567890&bank=VCB&amount=5000000"
                + "&des=INSABCDEF1234&template=compact&holder=NGUYEN+VAN+A", url);
    }

    @Test
    void buildQrUrl_noHolderWhenAccountNameBlank() {
        SepayPaymentGateway noHolder = new SepayPaymentGateway(SECRET, BANK, ACC, "", "");
        String url = noHolder.buildQrUrl(100_000L, "INSZZZZZZZZZZ");
        assertEquals("https://vietqr.app/img?acc=1234567890&bank=VCB&amount=100000"
                + "&des=INSZZZZZZZZZZ&template=compact", url);
        assertFalse(url.contains("holder"));
    }

    @Test
    void unconfiguredGateway_buildThrows_andAppWouldStillBoot() {
        SepayPaymentGateway unconfigured = new SepayPaymentGateway(SECRET, "", "", "", "");
        assertFalse(unconfigured.isConfigured());
        // Không fail ở construction/startup — chỉ fail khi thật sự tạo payment.
        assertThrows(IllegalStateException.class, () -> unconfigured.buildQrUrl(1000L, "INSAAAAAAAAAA"));
        assertTrue(gateway().isConfigured());
    }

    @Test
    void generateTransferContent_formatAndUniqueness() {
        String c1 = gateway().generateTransferContent();
        String c2 = gateway().generateTransferContent();
        assertTrue(c1.matches("INS[A-Z0-9]{10}"), c1);
        assertTrue(c2.matches("INS[A-Z0-9]{10}"), c2);
        assertNotEquals(c1, c2);
    }

    @Test
    void verify_validSignature_ok() {
        String body = "{\"id\":987654,\"transferType\":\"in\",\"transferAmount\":5000000}";
        String ts = nowTimestamp();
        PaymentGateway.VerificationResult result =
                gateway().verifyWebhookSignature(ts, body.getBytes(StandardCharsets.UTF_8), sign(SECRET, ts, body));
        assertTrue(result.valid());
    }

    @Test
    void verify_wrongSecret_rejected() {
        String body = "{\"id\":1,\"transferType\":\"in\"}";
        String ts = nowTimestamp();
        PaymentGateway.VerificationResult result =
                gateway().verifyWebhookSignature(ts, body.getBytes(StandardCharsets.UTF_8), sign("WRONGSECRET", ts, body));
        assertFalse(result.valid());
        assertEquals("invalid_signature", result.reason());
    }

    @Test
    void verify_missingSignature_rejected() {
        String body = "{\"id\":1}";
        PaymentGateway.VerificationResult result =
                gateway().verifyWebhookSignature(nowTimestamp(), body.getBytes(StandardCharsets.UTF_8), null);
        assertFalse(result.valid());
        assertEquals("missing_signature", result.reason());
    }

    @Test
    void verify_missingSecret_failClosed() {
        SepayPaymentGateway noSecret = new SepayPaymentGateway("", BANK, ACC, HOLDER, "");
        String body = "{\"id\":1}";
        String ts = nowTimestamp();
        PaymentGateway.VerificationResult result =
                noSecret.verifyWebhookSignature(ts, body.getBytes(StandardCharsets.UTF_8), sign(SECRET, ts, body));
        assertFalse(result.valid());
        assertEquals("gateway_not_configured", result.reason());
    }

    @Test
    void verify_expiredTimestamp_rejected() {
        String body = "{\"id\":1}";
        // Quá 300s: cả quá khứ và tương lai đều reject (chống replay).
        String oldTs = String.valueOf(Instant.now().getEpochSecond() - 301);
        assertFalse(gateway().verifyWebhookSignature(oldTs, body.getBytes(StandardCharsets.UTF_8),
                sign(SECRET, oldTs, body)).valid());
        String futureTs = String.valueOf(Instant.now().getEpochSecond() + 301);
        assertFalse(gateway().verifyWebhookSignature(futureTs, body.getBytes(StandardCharsets.UTF_8),
                sign(SECRET, futureTs, body)).valid());
        // Trong 300s thì pass.
        String okTs = String.valueOf(Instant.now().getEpochSecond() - 299);
        assertTrue(gateway().verifyWebhookSignature(okTs, body.getBytes(StandardCharsets.UTF_8),
                sign(SECRET, okTs, body)).valid());
    }

    @Test
    void verify_unparseableTimestamp_rejected() {
        String body = "{\"id\":1}";
        PaymentGateway.VerificationResult result =
                gateway().verifyWebhookSignature("not-a-number", body.getBytes(StandardCharsets.UTF_8),
                        "sha256=deadbeef");
        assertFalse(result.valid());
        assertEquals("invalid_timestamp", result.reason());
    }

    @Test
    void verify_rawBody_reserializedBodyFails() throws Exception {
        // Body gốc ký đúng; parse rồi serialize lại (thêm khoảng trắng/khác bytes)
        // thì chữ ký cũ PHẢI fail — chứng minh verify chạy trên RAW BODY, không
        // phải JSON serialize lại.
        ObjectMapper mapper = new ObjectMapper();
        String original = "{\"id\":987654,\"transferType\":\"in\",\"transferAmount\":5000000}";
        String ts = nowTimestamp();
        String signature = sign(SECRET, ts, original);
        assertTrue(gateway().verifyWebhookSignature(ts, original.getBytes(StandardCharsets.UTF_8), signature).valid());

        Map<String, Object> parsed = mapper.readValue(original, Map.class);
        String reserialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        assertNotEquals(original, reserialized);
        assertFalse(gateway().verifyWebhookSignature(ts, reserialized.getBytes(StandardCharsets.UTF_8), signature).valid());
    }

    @Test
    void contentMatches_codeExactMatch() {
        assertTrue(gateway().contentMatches("INSABCDEF1234", "chuyen tien", "INSABCDEF1234"));
        assertTrue(gateway().contentMatches("INSABCDEF1234", "chuyen tien", "insabcdef1234"));
        assertFalse(gateway().contentMatches("INSABCDEF1234", "chuyen tien", "INSOTHERCODE"));
    }

    @Test
    void contentMatches_extraWordsAndSpaces() {
        // Khách gõ thêm chữ, có khoảng trắng: uppercase + bỏ khoảng trắng rồi contains.
        assertTrue(gateway().contentMatches("INSABCDEF1234", "INSABCDEF1234 chuyen tien", null));
        assertTrue(gateway().contentMatches("INSABCDEF1234", "ins abcdef1234 thanh toan", null));
        assertTrue(gateway().contentMatches("INSABCDEF1234", "INSABCDEF1234", null));
        assertFalse(gateway().contentMatches("INSABCDEF1234", "thanh toan hop dong", null));
        assertFalse(gateway().contentMatches("INSABCDEF1234", null, null));
        assertFalse(gateway().contentMatches(null, "INSABCDEF1234", null));
    }
}
