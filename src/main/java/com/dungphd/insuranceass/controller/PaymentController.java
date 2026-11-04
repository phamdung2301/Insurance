package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PaymentResponse;
import com.dungphd.insuranceass.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Endpoint cổng VNPay (đợt payment).
 *
 * <p>Return URL chỉ tra cứu + hiển thị — KHÔNG đổi trạng thái.
 * IPN là nguồn sự thật duy nhất đổi trạng thái (verify chữ ký + khớp tiền).
 * IPN response KHÔNG bọc ApiResponse envelope: VNPay yêu cầu JSON thô
 * {"RspCode": "...", "Message": "..."}.
 *
 * <p>Chú ý: VNPay thực tế gọi IPN bằng GET (query string), trong khi task
 * đợt này ghi POST — controller hỗ trợ cả hai method, và SecurityConfig
 * permitAll cả GET + POST nhưng CHỈ đúng path IPN này.
 */
@RestController
@RequestMapping({"/payments", "/api/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * GET /payments/vnpay-return — FE trang kết quả gọi sau khi khách quay về
     * từ VNPay, truyền toàn bộ tham số vnp_* trên return URL (ít nhất txnRef).
     * Chỉ đọc: trả Payment + policyStatus hiện tại từ backend (nguồn sự thật).
     * Nếu có vnp_SecureHash mà verify fail → 403, không trả dữ liệu.
     * Yêu cầu đăng nhập + đúng scope đọc policy (không permitAll).
     */
    @GetMapping("/vnpay-return")
    public ResponseEntity<ApiResponse<PaymentResponse>> vnpayReturn(
            @RequestParam Map<String, String> allParams,
            Principal principal) {
        String txnRef = allParams.get("vnp_TxnRef");
        String actor = principal != null && principal.getName() != null ? principal.getName() : "System";
        PaymentResponse response = paymentService.lookupReturn(txnRef, allParams, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment status retrieved successfully"));
    }

    /**
     * POST /payments/vnpay-ipn (+ GET, xem class javadoc) — server-to-server
     * từ VNPay, KHÔNG có JWT → permitAll đúng path này trong SecurityConfig.
     * An toàn dựa vào verify chữ ký + khớp số tiền + idempotent theo txnRef,
     * không dựa vào permitAll.
     */
    @PostMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnpayIpnPost(@RequestParam Map<String, String> allParams) {
        return ResponseEntity.ok(paymentService.handleIpn(allParams));
    }

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnpayIpnGet(@RequestParam Map<String, String> allParams) {
        return ResponseEntity.ok(paymentService.handleIpn(allParams));
    }
}
