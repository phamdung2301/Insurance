package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.PaymentResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.GatewayPaymentStatus;
import com.dungphd.insuranceass.model.Payment;
import com.dungphd.insuranceass.model.PaymentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.repository.PaymentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.NotificationService;
import com.dungphd.insuranceass.service.PaymentGateway;
import com.dungphd.insuranceass.service.PaymentService;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thanh toán qua cổng VNPay sandbox.
 *
 * <p>QUYẾT ĐỊNH ĐÃ CHỐT (ghi rõ để parent quyết sau, không tự thêm logic):
 * <ul>
 *   <li>Luồng cổng đi QUOTED → ACTIVE trực tiếp bằng guard IPN riêng trong
 *       {@link #handleIpn}, KHÔNG tái dùng {@code transitionStatus} và KHÔNG
 *       sửa {@code PolicyStatus.ALLOWED_TRANSITIONS} — vì mở map chung sẽ mở
 *       luôn đường QUOTED → ACTIVE cho endpoint status-transitions của user.</li>
 *   <li>Luồng cổng BỎ QUA cả hai hook hoa hồng của luồng thủ công
 *       ({@code createForBound} / {@code markEarned} trong transitionStatus):
 *       cổng nhảy thẳng QUOTED → ACTIVE nên không đi qua BOUND. Đây là quyết
 *       định CỐ TÌNH TẠM THỜI trong đợt này (theo brief: ghi rõ, không tự thêm
 *       logic hoa hồng khi chưa rõ) — parent quyết sau có gọi bù hay không.</li>
 *   <li>Policy sau IPN thành công: {@code paymentStatus = CONFIRMED} (thống nhất
 *       "đã xác nhận thu tiền" như nhánh BOUND → ACTIVE) nhưng KHÔNG set
 *       {@code paymentClaimedAt} — field đó là marker riêng của luồng thủ công
 *       "Tôi đã chuyển khoản" (brief §1.2: "Luồng cổng mới không dùng field
 *       này"). Policy không có field paymentId/paidAt riêng; mã giao dịch và
 *       thời điểm trả nằm trên bản ghi {@link Payment} (gatewayTransactionNo,
 *       paidAt).</li>
 *   <li>Negotiation còn mở khi IPN ACTIVE: không tự đóng trong đợt này
 *       (transition BOUND đóng OPEN/PENDING_CONFIRMATION, nhưng luồng cổng
 *       chưa có quy định tương đương — ghi rõ để parent quyết; policy đã
 *       ACTIVE thì không còn đi tiếp luồng thương lượng).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter VNPAY_PAY_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PolicyRepository policyRepository;
    private final PaymentRepository paymentRepository;
    private final PermissionService permissionService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final TransactionLedgerService transactionLedgerService;

    @Override
    public PaymentResponse createPayment(String policyNumber, String principal, String clientIp) {
        Policy policy = findPolicy(policyNumber);
        if (principal == null || principal.isBlank()
                || !permissionService.isDemandSide(policy, principal)) {
            throw new ForbiddenException("Chỉ bên cầu của hợp đồng (người được bảo hiểm hoặc đại lý được giao) mới được tạo thanh toán.");
        }
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ tạo thanh toán khi hợp đồng ở trạng thái QUOTED. Trạng thái hiện tại: " + policy.getStatus());
        }
        if (policy.getTotalPremium() == null || policy.getTotalPremium() <= 0) {
            throw new BadRequestException("Hợp đồng chưa có tổng phí hợp lệ để thanh toán.");
        }
        // Giá đã deal phải khớp totalPremium; lệch = dữ liệu bất thường → từ chối, không tự sửa giá.
        if (policy.getNegotiatedPremium() != null
                && Double.compare(policy.getNegotiatedPremium(), policy.getTotalPremium()) != 0) {
            throw new BadRequestException("Giá thương lượng đã chốt không khớp tổng phí hợp đồng, không thể tạo thanh toán.");
        }
        if (!paymentGateway.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cổng thanh toán chưa được cấu hình (thiếu VNPAY_TMN_CODE hoặc VNPAY_HASH_SECRET).");
        }
        long amountVnd = Math.round(policy.getTotalPremium());

        // Supersede: payment PENDING cũ của cùng policy bị hủy khi tạo payment mới,
        // để tại một thời điểm chỉ có một payment chờ IPN. IPN của payment đã
        // CANCELLED sẽ bị từ chối ở handleIpn (RspCode 02), không kích hoạt policy.
        for (Payment old : paymentRepository.findByPolicyNumberAndStatus(
                policy.getPolicyNumber(), GatewayPaymentStatus.PENDING)) {
            old.setStatus(GatewayPaymentStatus.CANCELLED);
            old.setUpdatedAt(Instant.now());
            paymentRepository.save(old);
        }

        String txnRef = buildTxnRef(policy.getPolicyNumber());
        Payment payment = Payment.builder()
                .policyNumber(policy.getPolicyNumber())
                .amount(policy.getTotalPremium())
                .provider("VNPAY")
                .txnRef(txnRef)
                .status(GatewayPaymentStatus.PENDING)
                .createdBy(principal.trim())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Payment saved = paymentRepository.save(payment);
        String paymentUrl;
        try {
            paymentUrl = paymentGateway.buildPaymentUrl(
                    txnRef, amountVnd, "Thanh toan hop dong " + policy.getPolicyNumber(), clientIp);
        } catch (IllegalStateException e) {
            // Cấu hình đổi giữa chừng (hiếm): payment đã lưu PENDING, báo 503 rõ ràng.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        return toResponse(saved, paymentUrl, null, null);
    }

    @Override
    public List<PaymentResponse> listPayments(String policyNumber, String principal) {
        Policy policy = findPolicy(policyNumber);
        // Scope đúng rule đọc policy hiện tại: ADMIN/UW đọc mọi policy,
        // còn lại chỉ bên cầu (như PermissionService.checkPolicyRead, nhưng
        // nhận principal tường minh từ controller thay vì đọc SecurityContext).
        checkReadScope(policy, principal);
        return paymentRepository.findByPolicyNumberOrderByCreatedAtDesc(policy.getPolicyNumber())
                .stream()
                .map(p -> toResponse(p, null, policy.getStatus() != null ? policy.getStatus().name() : null, null))
                .toList();
    }

    @Override
    public PaymentResponse lookupReturn(String txnRef, Map<String, String> vnpParams, String principal) {
        if (txnRef == null || txnRef.isBlank()) {
            throw new BadRequestException("Thiếu mã giao dịch (vnp_TxnRef).");
        }
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán: " + txnRef));
        Policy policy = findPolicy(payment.getPolicyNumber());
        checkReadScope(policy, principal);
        boolean signatureValid = false;
        if (vnpParams != null && vnpParams.containsKey("vnp_SecureHash")) {
            PaymentGateway.VerificationResult result = paymentGateway.verifySignature(vnpParams);
            if (!result.valid()) {
                // Chống giả mạo hiển thị: chữ ký kèm theo mà sai thì không trả dữ liệu.
                throw new ForbiddenException("Chữ ký VNPay không hợp lệ.");
            }
            signatureValid = true;
        }
        return toResponse(payment, null,
                policy.getStatus() != null ? policy.getStatus().name() : null, signatureValid);
    }

    @Override
    public Map<String, String> handleIpn(Map<String, String> vnpParams) {
        String txnRef = vnpParams != null ? vnpParams.get("vnp_TxnRef") : null;
        if (txnRef == null || txnRef.isBlank()) {
            return rsp("01", "Order not found");
        }
        // 1. Verify chữ ký TRƯỚC mọi xử lý trạng thái. Không log chữ ký/secret.
        PaymentGateway.VerificationResult verified = paymentGateway.verifySignature(vnpParams);
        if (!verified.valid()) {
            log.warn("VNPay IPN rejected: invalid signature for txnRef {}", txnRef);
            return rsp("97", "Invalid signature");
        }
        // 2. TxnRef phải tồn tại, không tạo payment/policy mới từ IPN.
        Payment payment = paymentRepository.findByTxnRef(txnRef).orElse(null);
        if (payment == null) {
            log.warn("VNPay IPN rejected: unknown txnRef {}", txnRef);
            return rsp("01", "Order not found");
        }
        // 3. Idempotent: đã PAID thì trả success, không chuyển đổi lại, không gửi mail lại.
        if (payment.getStatus() == GatewayPaymentStatus.PAID) {
            return rsp("00", "Confirm Success");
        }
        // Payment đã ở trạng thái cuối khác (FAILED/CANCELLED, vd bị supersede) → không xử lý nữa.
        if (payment.getStatus() != GatewayPaymentStatus.PENDING) {
            return rsp("02", "Order already processed");
        }
        // 4. Khớp số tiền: VNPay amount (x100) so với amount đã khóa lúc tạo payment.
        Long ipnAmountVnd = paymentGateway.parseMinorAmountToVnd(vnpParams.get("vnp_Amount"));
        long expectedVnd = Math.round(payment.getAmount() != null ? payment.getAmount() : 0.0);
        if (ipnAmountVnd == null || ipnAmountVnd != expectedVnd) {
            // Giữ PENDING để FINANCE đối soát tay — không tự ACTIVE, không tự FAILED hủy bằng chứng.
            log.warn("VNPay IPN rejected: amount mismatch for txnRef {} (ipn={}, expected={})",
                    txnRef, vnpParams.get("vnp_Amount"), expectedVnd);
            return rsp("04", "Invalid amount");
        }
        // 5. Response code VNPay: khác "00" = thất bại/hủy → policy giữ QUOTED, không gửi mail thành công.
        String responseCode = vnpParams.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            payment.setStatus("24".equals(responseCode)
                    ? GatewayPaymentStatus.CANCELLED : GatewayPaymentStatus.FAILED);
            payment.setGatewayTransactionNo(vnpParams.get("vnp_TransactionNo"));
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            return rsp("00", "Confirm Success");
        }
        // 6. Guard QUOTED → ACTIVE riêng cho IPN (không qua transitionStatus).
        Policy policy = policyRepository.findByPolicyNumber(payment.getPolicyNumber())
                .or(() -> policyRepository.findById(payment.getPolicyNumber()))
                .orElse(null);
        if (policy == null) {
            log.warn("VNPay IPN: policy not found for txnRef {}", txnRef);
            return rsp("01", "Order not found");
        }
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            // Tiền đã về nhưng policy không còn QUOTED (đã ACTIVE qua luồng khác
            // hoặc đã CANCELLED): không chuyển đổi lại, giữ payment PENDING để
            // đối soát tay, không tự hoàn tiền trong đợt này.
            log.warn("VNPay IPN held for manual review: policy {} not QUOTED (status={}) for txnRef {}",
                    policy.getPolicyNumber(), policy.getStatus(), txnRef);
            return rsp("99", "Policy not in QUOTED state");
        }
        Instant now = Instant.now();
        Instant effective = policy.getEffectiveDate() != null ? policy.getEffectiveDate() : now;
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setEffectiveDate(effective);
        policy.setExpirationDate(effective.plus(365, ChronoUnit.DAYS));
        // Thống nhất "đã xác nhận thu tiền" như nhánh BOUND → ACTIVE; KHÔNG set
        // paymentClaimedAt (marker riêng của luồng thủ công, xem class javadoc).
        policy.setPaymentStatus(PaymentStatus.CONFIRMED);
        policy.setUpdatedAt(now);
        Policy savedPolicy = policyRepository.save(policy);

        payment.setStatus(GatewayPaymentStatus.PAID);
        payment.setGatewayTransactionNo(vnpParams.get("vnp_TransactionNo"));
        payment.setPaidAt(parsePayDate(vnpParams.get("vnp_PayDate"), now));
        payment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.save(payment);

        try {
            transactionLedgerService.recordTransaction(
                    savedPolicy.getPolicyNumber(), savedPolicy.getVersion(),
                    TransactionType.STATUS_TRANSITION, "VNPAY-IPN",
                    "Thanh toan qua VNPay thanh cong (txnRef=" + txnRef + "), hop dong QUOTED -> ACTIVE");
        } catch (Exception e) {
            log.warn("Failed to record ledger for VNPay IPN txnRef {}: {}", txnRef, e.getMessage());
        }
        // Mail + in-app: failure-isolated bên trong NotificationService, và chỉ
        // gọi ở nhánh IPN thành công này (không gắn vào luồng thủ công cũ).
        // Hoa hồng createForBound/markEarned CỐ TÌNH không gọi ở đây (xem class javadoc).
        try {
            notificationService.notifyPaymentSucceeded(savedPolicy, savedPayment);
        } catch (Exception e) {
            log.warn("Failed to send payment-succeeded notification for txnRef {}: {}", txnRef, e.getMessage());
        }
        return rsp("00", "Confirm Success");
    }

    /** ADMIN/UW đọc mọi policy, còn lại chỉ bên cầu — đúng canReadPolicy. */
    private void checkReadScope(Policy policy, String principal) {
        if (principal == null || principal.isBlank()) {
            throw new ForbiddenException("Bạn không có quyền xem hợp đồng này.");
        }
        if (permissionService.isAdmin() || permissionService.isUnderwriter()
                || permissionService.isDemandSide(policy, principal)) {
            return;
        }
        throw new ForbiddenException("Bạn không có quyền xem hợp đồng này.");
    }

    private Policy findPolicy(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));
    }

    private static String buildTxnRef(String policyNumber) {
        String clean = policyNumber == null ? "POL"
                : policyNumber.replaceAll("[^A-Za-z0-9]", "");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return clean + "-" + Instant.now().getEpochSecond() + "-" + suffix;
    }

    private static Instant parsePayDate(String vnpPayDate, Instant fallback) {
        if (vnpPayDate == null || vnpPayDate.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(vnpPayDate.trim(), VNPAY_PAY_DATE)
                    .atOffset(ZoneOffset.ofHours(7)).toInstant();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, String> rsp(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private static PaymentResponse toResponse(Payment p, String paymentUrl, String policyStatus, Boolean signatureValid) {
        return PaymentResponse.builder()
                .id(p.getId())
                .policyNumber(p.getPolicyNumber())
                .amount(p.getAmount())
                .provider(p.getProvider())
                .txnRef(p.getTxnRef())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .gatewayTransactionNo(p.getGatewayTransactionNo())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .paymentUrl(paymentUrl)
                .policyStatus(policyStatus)
                .signatureValid(signatureValid)
                .build();
    }
}
