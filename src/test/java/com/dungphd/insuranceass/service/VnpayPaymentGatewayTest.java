package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.service.impl.VnpayPaymentGateway;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnpayPaymentGatewayTest {

    private static final String TMN = "TESTTMN01";
    private static final String SECRET = "TESTHASHSECRET1234567890ABCDEF";

    private VnpayPaymentGateway gateway() {
        return new VnpayPaymentGateway(TMN, SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:8080/payments/vnpay-ipn");
    }

    /** Parse query string của paymentUrl thành map (để verify lại chữ ký). */
    private static Map<String, String> parseQuery(String url) {
        Map<String, String> params = new HashMap<>();
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    @Test
    void buildPaymentUrl_signatureVerifies() {
        String url = gateway().buildPaymentUrl("POL001-123-ABCDEF", 5_000_000L,
                "Thanh toan hop dong POL001", "127.0.0.1");
        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        Map<String, String> params = parseQuery(url);
        assertEquals(TMN, params.get("vnp_TmnCode"));
        assertEquals("500000000", params.get("vnp_Amount")); // VND x 100
        assertEquals("POL001-123-ABCDEF", params.get("vnp_TxnRef"));
        assertNotNull(params.get("vnp_SecureHash"));
        assertTrue(gateway().verifySignature(params).valid());
    }

    @Test
    void verifySignature_tamperedAmount_rejected() {
        String url = gateway().buildPaymentUrl("POL001-123-ABCDEF", 5_000_000L,
                "Thanh toan hop dong POL001", "127.0.0.1");
        Map<String, String> params = parseQuery(url);
        params.put("vnp_Amount", "100");
        assertFalse(gateway().verifySignature(params).valid());
    }

    @Test
    void verifySignature_wrongSecret_rejected() {
        String url = gateway().buildPaymentUrl("POL001-123-ABCDEF", 5_000_000L,
                "Thanh toan hop dong POL001", "127.0.0.1");
        Map<String, String> params = parseQuery(url);
        VnpayPaymentGateway otherSecret = new VnpayPaymentGateway(TMN, "DIFFERENTSECRET",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "", "");
        assertFalse(otherSecret.verifySignature(params).valid());
    }

    @Test
    void verifySignature_missingSignature_rejected() {
        Map<String, String> params = Map.of("vnp_TxnRef", "X", "vnp_Amount", "100");
        PaymentGateway.VerificationResult result = gateway().verifySignature(params);
        assertFalse(result.valid());
        assertEquals("missing_signature", result.reason());
    }

    @Test
    void unconfiguredGateway_buildThrows_andAppWouldStillBoot() {
        VnpayPaymentGateway unconfigured = new VnpayPaymentGateway("", "", "", "", "");
        assertFalse(unconfigured.isConfigured());
        // Không fail ở construction/startup — chỉ fail khi thật sự tạo payment.
        assertThrows(IllegalStateException.class, () ->
                unconfigured.buildPaymentUrl("TXN", 1000L, "test", "127.0.0.1"));
        assertTrue(gateway().isConfigured());
    }

    @Test
    void parseMinorAmountToVnd() {
        assertEquals(5_000_000L, gateway().parseMinorAmountToVnd("500000000"));
        assertNull(gateway().parseMinorAmountToVnd("500000001")); // không chia hết cho 100
        assertNull(gateway().parseMinorAmountToVnd("abc"));
        assertNull(gateway().parseMinorAmountToVnd(null));
    }
}
