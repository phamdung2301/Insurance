package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PaymentResponse;
import com.dungphd.insuranceass.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Endpoint cổng SePay (chuyển khoản + webhook).
 *
 * <p>Webhook là nguồn sự thật duy nhất đổi trạng thái: SePay gửi POST JSON
 * tới {@code /payments/sepay-webhook} (không JWT → permitAll ĐÚNG path +
 * ĐÚNG method POST này trong SecurityConfig; an toàn dựa vào verify
 * HMAC-SHA256 trên RAW BODY + khớp tiền + idempotent trong
 * PaymentServiceImpl). Webhook response KHÔNG bọc ApiResponse envelope:
 * SePay yêu cầu JSON thô {@code {"success": true/false}}.
 *
 * <p>Tra cứu trạng thái chỉ đọc — KHÔNG đổi trạng thái, KHÔNG gửi mail —
 * và vẫn cần JWT + đúng scope đọc policy (không permitAll).
 */
@RestController
@RequestMapping({"/payments", "/api/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /payments/sepay-webhook — server-to-server từ SePay, KHÔNG có JWT.
     * Nhận RAW BODY (byte[]) để verify HMAC trước khi parse JSON — KHÔNG
     * parse DTO rồi serialize lại để verify (bytes khác là chữ ký khác).
     * Chỉ lỗi xác thực mới trả 401; mọi lỗi dữ liệu trả 200 success + log
     * (SePay retry cũng vô ích) — xem PaymentService.handleSepayWebhook.
     */
    @PostMapping("/sepay-webhook")
    public ResponseEntity<Map<String, Boolean>> sepayWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp) {
        PaymentService.WebhookResult result =
                paymentService.handleSepayWebhook(rawBody, signature, timestamp);
        return ResponseEntity.status(result.httpStatus()).body(Map.of("success", result.success()));
    }

    /**
     * GET /payments/status?txnRef=... — FE tra cứu sau khi khách chuyển khoản.
     * Chỉ đọc: trả Payment + policyStatus hiện tại từ backend (nguồn sự thật).
     * Yêu cầu đăng nhập + đúng scope đọc policy (không permitAll).
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PaymentResponse>> paymentStatus(
            @RequestParam String txnRef,
            Principal principal) {
        String actor = principal != null && principal.getName() != null ? principal.getName() : "System";
        PaymentResponse response = paymentService.lookupStatus(txnRef, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment status retrieved successfully"));
    }
}
