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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Thanh toán qua cổng SePay (chuyển khoản + webhook "Có tiền vào").
 *
 * <p>QUYẾT ĐỊNH ĐÃ CHỐT (ghi rõ để parent quyết sau, không tự thêm logic):
 * <ul>
 *   <li>Luồng cổng đi QUOTED → ACTIVE trực tiếp bằng guard webhook riêng
 *       trong {@link #handleSepayWebhook}, KHÔNG tái dùng
 *       {@code transitionStatus} và KHÔNG sửa
 *       {@code PolicyStatus.ALLOWED_TRANSITIONS} — vì mở map chung sẽ mở
 *       luôn đường QUOTED → ACTIVE cho endpoint status-transitions của user.</li>
 *   <li>Luồng cổng BỎ QUA cả hai hook hoa hồng của luồng thủ công
 *       ({@code createForBound} / {@code markEarned} trong transitionStatus):
 *       cổng nhảy thẳng QUOTED → ACTIVE nên không đi qua BOUND. Đây là quyết
 *       định CỐ TÌNH TẠM THỜI trong đợt này (theo brief: ghi rõ, không tự thêm
 *       logic hoa hồng khi chưa rõ) — parent quyết sau có gọi bù hay không.</li>
 *   <li>Policy sau webhook thành công: {@code paymentStatus = CONFIRMED} (thống nhất
 *       "đã xác nhận thu tiền" như nhánh BOUND → ACTIVE) nhưng KHÔNG set
 *       {@code paymentClaimedAt} — field đó là marker riêng của luồng thủ công
 *       "Tôi đã chuyển khoản" (brief: "Luồng cổng mới không dùng field
 *       này"). Policy không có field paymentId/paidAt riêng; mã giao dịch và
 *       thời điểm trả nằm trên bản ghi {@link Payment} (gatewayTransactionNo,
 *       paidAt).</li>
 *   <li>Negotiation còn mở khi webhook ACTIVE: không tự đóng trong đợt này
 *       (transition BOUND đóng OPEN/PENDING_CONFIRMATION, nhưng luồng cổng
 *       chưa có quy định tương đương — ghi rõ để parent quyết; policy đã
 *       ACTIVE thì không còn đi tiếp luồng thương lượng).</li>
 *   <li>Webhook là nguồn sự thật duy nhất đổi trạng thái. SePay retry tối đa
 *       7 lần trong 5h nếu không nhận 200/201 + {@code {"success": true}},
 *       nên chỉ lỗi xác thực mới trả 401; mọi lỗi dữ liệu trả 200 success +
 *       log (retry cũng vô ích), payment giữ PENDING để đối soát tay khi
 *       sai tiền hoặc policy không còn QUOTED.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int TRANSFER_CONTENT_MAX_ATTEMPTS = 10;

    private final PolicyRepository policyRepository;
    private final PaymentRepository paymentRepository;
    private final PermissionService permissionService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final TransactionLedgerService transactionLedgerService;
    /** Parser webhook tự tạo (không inject bean: app không expose ObjectMapper bean). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentResponse createPayment(String policyNumber, String principal) {
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
                    "Cổng thanh toán SePay chưa được cấu hình (thiếu SEPAY_BANK_CODE hoặc SEPAY_ACCOUNT_NUMBER).");
        }
        long amountVnd = Math.round(policy.getTotalPremium());

        // Supersede: payment PENDING cũ của cùng policy bị hủy khi tạo payment mới,
        // để tại một thời điểm chỉ có một payment chờ webhook. Webhook của payment đã
        // CANCELLED sẽ bị bỏ qua ở handleSepayWebhook (trạng thái cuối), không kích hoạt policy.
        for (Payment old : paymentRepository.findByPolicyNumberAndStatus(
                policy.getPolicyNumber(), GatewayPaymentStatus.PENDING)) {
            old.setStatus(GatewayPaymentStatus.CANCELLED);
            old.setUpdatedAt(Instant.now());
            paymentRepository.save(old);
        }

        String txnRef = buildTxnRef(policy.getPolicyNumber());
        String transferContent = generateUniqueTransferContent();
        Payment payment = Payment.builder()
                .policyNumber(policy.getPolicyNumber())
                .amount(policy.getTotalPremium())
                .provider("SEPAY")
                .txnRef(txnRef)
                .transferContent(transferContent)
                .status(GatewayPaymentStatus.PENDING)
                .createdBy(principal.trim())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Payment saved = paymentRepository.save(payment);
        String qrUrl;
        try {
            qrUrl = paymentGateway.buildQrUrl(amountVnd, transferContent);
        } catch (IllegalStateException e) {
            // Cấu hình đổi giữa chừng (hiếm): payment đã lưu PENDING, báo 503 rõ ràng.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        return toCreateResponse(saved, qrUrl);
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
                .map(p -> toResponse(p, p.getTransferContent(),
                        policy.getStatus() != null ? policy.getStatus().name() : null))
                .toList();
    }

    @Override
    public PaymentResponse lookupStatus(String txnRef, String principal) {
        if (txnRef == null || txnRef.isBlank()) {
            throw new BadRequestException("Thiếu mã giao dịch (txnRef).");
        }
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán: " + txnRef));
        Policy policy = findPolicy(payment.getPolicyNumber());
        checkReadScope(policy, principal);
        // CHỈ ĐỌC: không đổi Payment, không đổi Policy, không gửi mail.
        return PaymentResponse.builder()
                .txnRef(payment.getTxnRef())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .policyStatus(policy.getStatus() != null ? policy.getStatus().name() : null)
                .amount(payment.getAmount())
                .provider(payment.getProvider())
                .build();
    }

    @Override
    public WebhookResult handleSepayWebhook(byte[] rawBody, String signatureHeader, String timestampHeader) {
        // 1. Verify HMAC-SHA256 trên RAW BODY TRƯỚC mọi xử lý trạng thái.
        // Không log chữ ký/secret. Chỉ lỗi xác thực mới trả 401.
        PaymentGateway.VerificationResult verified =
                paymentGateway.verifyWebhookSignature(timestampHeader, rawBody, signatureHeader);
        if (!verified.valid()) {
            log.warn("SePay webhook rejected: authentication failed ({})", verified.reason());
            return WebhookResult.unauthorized();
        }
        // 2. Parse JSON — parse lỗi là lỗi dữ liệu → 200 success + log, retry vô ích.
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("SePay webhook ignored: unparseable body: {}", e.getMessage());
            return WebhookResult.ok();
        }
        // 3. Chỉ xử lý tiền vào; tiền ra ("out") và loại khác → bỏ qua, 200 success.
        String transferType = textField(root, "transferType");
        if (!"in".equalsIgnoreCase(transferType)) {
            log.info("SePay webhook ignored: transferType={} (only 'in' is processed)", transferType);
            return WebhookResult.ok();
        }
        // 4. Tìm payment theo transferContent trong content/code. Không khớp →
        // 200 success + log, KHÔNG tạo payment/policy mới từ webhook.
        Payment payment = findPaymentForWebhook(root);
        if (payment == null) {
            log.info("SePay webhook ignored: no payment matches content/code (sepayId={}, code={})",
                    textField(root, "id"), textField(root, "code"));
            return WebhookResult.ok();
        }
        // 5. Khớp tài khoản nhận (chỉ khi SEPAY_ACCOUNT_NUMBER có set): lệch →
        // 200 success + warn, không đổi trạng thái.
        String configuredAccount = paymentGateway.getAccountNumber();
        String webhookAccount = textField(root, "accountNumber");
        if (configuredAccount != null && !configuredAccount.isBlank()
                && webhookAccount != null && !webhookAccount.equals(configuredAccount)) {
            log.warn("SePay webhook held: account mismatch for txnRef {} (webhook={}, expected={})",
                    payment.getTxnRef(), webhookAccount, configuredAccount);
            return WebhookResult.ok();
        }
        // 6. Khớp số tiền: transferAmount (VND nguyên) so với amount đã khóa lúc tạo.
        Long webhookAmount = longField(root, "transferAmount");
        long expectedVnd = Math.round(payment.getAmount() != null ? payment.getAmount() : 0.0);
        if (webhookAmount == null || webhookAmount != expectedVnd) {
            // Giữ PENDING để đối soát tay — không tự ACTIVE, không tự FAILED hủy bằng chứng.
            log.warn("SePay webhook held: amount mismatch for txnRef {} (webhook={}, expected={})",
                    payment.getTxnRef(), webhookAmount, expectedVnd);
            return WebhookResult.ok();
        }
        // 7. Idempotent: SePay id đã xử lý (trên payment này hoặc payment khác),
        // hoặc payment đã ở trạng thái cuối → 200 success, không chuyển đổi lại, không gửi mail lại.
        String sepayId = textField(root, "id");
        if (sepayId != null && !sepayId.isBlank()) {
            boolean alreadyProcessed = sepayId.equals(payment.getGatewayTransactionNo())
                    || paymentRepository.findByGatewayTransactionNo(sepayId).isPresent();
            if (alreadyProcessed) {
                log.info("SePay webhook duplicate ignored: sepayId={} already processed", sepayId);
                return WebhookResult.ok();
            }
        }
        if (payment.getStatus() != GatewayPaymentStatus.PENDING) {
            log.info("SePay webhook ignored: payment {} already in terminal status {}",
                    payment.getTxnRef(), payment.getStatus());
            return WebhookResult.ok();
        }
        // 8. Guard QUOTED → ACTIVE riêng cho webhook (không qua transitionStatus).
        Policy policy = policyRepository.findByPolicyNumber(payment.getPolicyNumber())
                .or(() -> policyRepository.findById(payment.getPolicyNumber()))
                .orElse(null);
        if (policy == null) {
            log.warn("SePay webhook: policy not found for txnRef {}", payment.getTxnRef());
            return WebhookResult.ok();
        }
        if (policy.getStatus() != PolicyStatus.QUOTED) {
            // Tiền đã về nhưng policy không còn QUOTED (đã ACTIVE qua luồng khác
            // hoặc đã CANCELLED): không chuyển đổi lại, giữ payment PENDING để
            // đối soát tay, không tự hoàn tiền trong đợt này.
            log.warn("SePay webhook held for manual review: policy {} not QUOTED (status={}) for txnRef {}",
                    policy.getPolicyNumber(), policy.getStatus(), payment.getTxnRef());
            return WebhookResult.ok();
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
        payment.setGatewayTransactionNo(sepayId);
        payment.setPaidAt(parseTransactionDate(textField(root, "transactionDate"), now));
        payment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.save(payment);

        try {
            transactionLedgerService.recordTransaction(
                    savedPolicy.getPolicyNumber(), savedPolicy.getVersion(),
                    TransactionType.STATUS_TRANSITION, "SEPAY-WEBHOOK",
                    "Thanh toan qua SePay thanh cong (txnRef=" + payment.getTxnRef()
                            + "), hop dong QUOTED -> ACTIVE");
        } catch (Exception e) {
            log.warn("Failed to record ledger for SePay txnRef {}: {}", payment.getTxnRef(), e.getMessage());
        }
        // Mail + in-app: failure-isolated bên trong NotificationService, và chỉ
        // gọi ở nhánh webhook thành công này (không gắn vào luồng thủ công cũ).
        // Hoa hồng createForBound/markEarned CỐ TÌNH không gọi ở đây (xem class javadoc).
        try {
            notificationService.notifyPaymentSucceeded(savedPolicy, savedPayment);
        } catch (Exception e) {
            log.warn("Failed to send payment-succeeded notification for txnRef {}: {}",
                    payment.getTxnRef(), e.getMessage());
        }
        return WebhookResult.ok();
    }

    /**
     * Tìm payment cho webhook: (a) khớp đúng field {@code code} của SePay
     * (mọi trạng thái — để webhook trùng của payment đã PAID vẫn short-circuit
     * ở bước idempotent); (b) nếu không, quét các payment PENDING và kiểm tra
     * transferContent nằm trong content (khách gõ thêm chữ).
     */
    private Payment findPaymentForWebhook(JsonNode root) {
        String code = textField(root, "code");
        if (code != null && !code.isBlank()) {
            Payment byCode = paymentRepository.findByTransferContent(code.trim().toUpperCase()).orElse(null);
            if (byCode != null) {
                return byCode;
            }
        }
        String content = textField(root, "content");
        if (content == null || content.isBlank()) {
            return null;
        }
        for (Payment pending : paymentRepository.findByStatus(GatewayPaymentStatus.PENDING)) {
            if (paymentGateway.contentMatches(pending.getTransferContent(), content, code)) {
                return pending;
            }
        }
        return null;
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

    /** Sinh transferContent + kiểm tra duy nhất qua repository (cộng unique index). */
    private String generateUniqueTransferContent() {
        for (int i = 0; i < TRANSFER_CONTENT_MAX_ATTEMPTS; i++) {
            String candidate = paymentGateway.generateTransferContent();
            if (!paymentRepository.existsByTransferContent(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Không sinh được nội dung chuyển khoản duy nhất, vui lòng thử lại.");
    }

    private static String textField(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private static Long longField(JsonNode root, String field) {
        if (root == null || !root.has(field) || !root.get(field).isNumber()) {
            return null;
        }
        return root.get(field).asLong();
    }

    /**
     * Parse transactionDate của SePay, thử lần lượt: Instant ISO, LocalDateTime
     * ISO, "yyyy-MM-dd HH:mm:ss" (giờ VN, UTC+7). Parse lỗi → fallback now.
     */
    private static Instant parseTransactionDate(String transactionDate, Instant fallback) {
        if (transactionDate == null || transactionDate.isBlank()) {
            return fallback;
        }
        String value = transactionDate.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Thử các dạng LocalDateTime tiếp theo.
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atOffset(ZoneOffset.ofHours(7)).toInstant();
        } catch (DateTimeParseException ignored) {
            // Thử dạng "yyyy-MM-dd HH:mm:ss" tiếp theo.
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atOffset(ZoneOffset.ofHours(7)).toInstant();
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private PaymentResponse toCreateResponse(Payment p, String qrUrl) {
        String accountName = paymentGateway.getAccountName();
        return PaymentResponse.builder()
                .id(p.getId())
                .policyNumber(p.getPolicyNumber())
                .amount(p.getAmount())
                .provider(p.getProvider())
                .txnRef(p.getTxnRef())
                .transferContent(p.getTransferContent())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .gatewayTransactionNo(p.getGatewayTransactionNo())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .bankCode(paymentGateway.getBankCode())
                .accountNumber(paymentGateway.getAccountNumber())
                .accountName(accountName != null && !accountName.isBlank() ? accountName : null)
                .qrUrl(qrUrl)
                .build();
    }

    private static PaymentResponse toResponse(Payment p, String transferContent, String policyStatus) {
        return PaymentResponse.builder()
                .id(p.getId())
                .policyNumber(p.getPolicyNumber())
                .amount(p.getAmount())
                .provider(p.getProvider())
                .txnRef(p.getTxnRef())
                .transferContent(transferContent)
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .gatewayTransactionNo(p.getGatewayTransactionNo())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .policyStatus(policyStatus)
                .build();
    }
}
