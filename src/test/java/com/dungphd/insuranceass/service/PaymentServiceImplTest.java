package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.PaymentResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.GatewayPaymentStatus;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Payment;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PaymentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TransactionLedgerService transactionLedgerService;

    private PaymentServiceImpl paymentService;

    private Policy quotedPolicy;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        // Service tự build (không @InjectMocks): constructor chỉ nhận các
        // collaborator mock; ObjectMapper webhook do service tự tạo.
        paymentService = new PaymentServiceImpl(policyRepository, paymentRepository,
                permissionService, paymentGateway, notificationService,
                transactionLedgerService);
        quotedPolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-1001")
                .status(PolicyStatus.QUOTED)
                .insured(Insured.builder().name("Nguyễn Văn A").email("customer@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .totalPremium(5_000_000.0)
                .build();
        pendingPayment = Payment.builder()
                .id("pay-1")
                .policyNumber("POL-1001")
                .amount(5_000_000.0)
                .provider("SEPAY")
                .txnRef("POL1001-123-ABCDEF")
                .transferContent("INSABCDEF1234")
                .status(GatewayPaymentStatus.PENDING)
                .build();
        lenient().when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(quotedPolicy));
        lenient().when(policyRepository.findById("POL-1001")).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentGateway.getBankCode()).thenReturn("VCB");
        lenient().when(paymentGateway.getAccountNumber()).thenReturn("1234567890");
        lenient().when(paymentGateway.getAccountName()).thenReturn("NGUYEN VAN A");
    }

    private String webhookBody(String sepayId, String transferType, String content,
                               String code, String accountNumber, long amount) {
        return "{\"id\":" + sepayId
                + ",\"gateway\":\"Vietcombank\""
                + ",\"transactionDate\":\"2026-11-04 12:00:00\""
                + ",\"accountNumber\":\"" + accountNumber + "\""
                + ",\"code\":\"" + code + "\""
                + ",\"content\":\"" + content + "\""
                + ",\"transferType\":\"" + transferType + "\""
                + ",\"description\":\"" + content + "\""
                + ",\"transferAmount\":" + amount
                + ",\"referenceCode\":\"SEPAYREF1\"}";
    }

    private PaymentService.WebhookResult webhookOk(String body) {
        return paymentService.handleSepayWebhook(
                body.getBytes(StandardCharsets.UTF_8), "sha256=valid", "1234567890");
    }

    private void stubVerifiedWebhook() {
        when(paymentGateway.verifyWebhookSignature(any(), any(), any()))
                .thenReturn(PaymentGateway.VerificationResult.ok());
    }

    @Test
    void createPayment_quotedAndDemandSide_returnsQrAndPending() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentGateway.generateTransferContent()).thenReturn("INSABCDEF1234");
        when(paymentGateway.buildQrUrl(5_000_000L, "INSABCDEF1234"))
                .thenReturn("https://vietqr.app/img?acc=1234567890&bank=VCB&amount=5000000&des=INSABCDEF1234&template=compact");

        PaymentResponse res = paymentService.createPayment("POL-1001", "customer@example.com");
        assertEquals("PENDING", res.getStatus());
        assertEquals("SEPAY", res.getProvider());
        assertEquals("INSABCDEF1234", res.getTransferContent());
        assertEquals("VCB", res.getBankCode());
        assertEquals("1234567890", res.getAccountNumber());
        assertEquals("NGUYEN VAN A", res.getAccountName());
        assertNotNull(res.getQrUrl());
        assertTrue(res.getQrUrl().contains("INSABCDEF1234"));
        assertNull(res.getPolicyStatus());
        assertEquals(5_000_000.0, res.getAmount());
        assertNotNull(res.getTxnRef());
    }

    @Test
    void createPayment_twoPayments_haveDifferentTransferContent() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentGateway.generateTransferContent())
                .thenReturn("INSAAAAAAAAAA", "INSBBBBBBBBBB");
        when(paymentGateway.buildQrUrl(anyLong(), anyString()))
                .thenAnswer(inv -> "https://vietqr.app/img?des=" + inv.getArgument(1));

        PaymentResponse first = paymentService.createPayment("POL-1001", "customer@example.com");
        PaymentResponse second = paymentService.createPayment("POL-1001", "customer@example.com");
        assertNotEquals(first.getTransferContent(), second.getTransferContent());
    }

    @Test
    void createPayment_duplicateCandidate_retriedUntilUnique() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentGateway.generateTransferContent())
                .thenReturn("INSDUPLICATE1", "INSUNIQUE0001");
        when(paymentRepository.existsByTransferContent("INSDUPLICATE1")).thenReturn(true);
        when(paymentRepository.existsByTransferContent("INSUNIQUE0001")).thenReturn(false);
        when(paymentGateway.buildQrUrl(anyLong(), anyString())).thenReturn("https://vietqr.app/img?x=1");

        PaymentResponse res = paymentService.createPayment("POL-1001", "customer@example.com");
        assertEquals("INSUNIQUE0001", res.getTransferContent());
    }

    @Test
    void createPayment_supersedesOldPending() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        Payment old = Payment.builder().policyNumber("POL-1001").status(GatewayPaymentStatus.PENDING).build();
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of(old));
        when(paymentGateway.generateTransferContent()).thenReturn("INSNEW000001");
        when(paymentGateway.buildQrUrl(anyLong(), anyString())).thenReturn("https://vietqr.app/img?x=1");

        paymentService.createPayment("POL-1001", "customer@example.com");
        assertEquals(GatewayPaymentStatus.CANCELLED, old.getStatus());
    }

    @Test
    void createPayment_notDemandSide_forbidden() {
        when(permissionService.isDemandSide(quotedPolicy, "other@example.com")).thenReturn(false);
        assertThrows(ForbiddenException.class, () ->
                paymentService.createPayment("POL-1001", "other@example.com"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_notQuoted_conflict() {
        quotedPolicy.setStatus(PolicyStatus.BOUND);
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createPayment_gatewayNotConfigured_serviceUnavailable() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("SEPAY_BANK_CODE"));
    }

    @Test
    void createPayment_negotiatedMismatch_rejected() {
        quotedPolicy.setNegotiatedPremium(4_000_000.0);
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        assertThrows(Exception.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com"));
    }

    @Test
    void webhook_validInAmount_activatesOnceAndNotifies() {
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByGatewayTransactionNo("987654")).thenReturn(Optional.empty());

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234 chuyen tien", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        assertEquals(PolicyStatus.ACTIVE, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("987654", pendingPayment.getGatewayTransactionNo());
        assertNotNull(pendingPayment.getPaidAt());
        assertNotNull(quotedPolicy.getEffectiveDate());
        assertNotNull(quotedPolicy.getExpirationDate());
        verify(notificationService, times(1)).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_contentWithExtraWords_matchesWithoutCode() {
        stubVerifiedWebhook();
        // Không khớp code (code rỗng) → rơi sang quét PENDING + contains trong content.
        when(paymentRepository.findByStatus(GatewayPaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));
        when(paymentGateway.contentMatches("INSABCDEF1234", "INSABCDEF1234 chuyen tien", ""))
                .thenReturn(true);
        when(paymentRepository.findByGatewayTransactionNo("987654")).thenReturn(Optional.empty());

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234 chuyen tien", "", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertEquals(PolicyStatus.ACTIVE, quotedPolicy.getStatus());
        verify(notificationService, times(1)).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_duplicateSepayId_idempotentNoMailAgain() {
        pendingPayment.setStatus(GatewayPaymentStatus.PAID);
        pendingPayment.setGatewayTransactionNo("987654");
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));
        // Không stub findByGatewayTransactionNo: payment đã mang đúng sepayId nên
        // code short-circuit bằng equals trước khi chạm repository.

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus()); // không chuyển đổi lại
        verify(policyRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_sepayIdAlreadyOnAnotherPayment_idempotentNoMail() {
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));
        Payment alreadyPaid = Payment.builder().txnRef("OTHER-1")
                .gatewayTransactionNo("987654").status(GatewayPaymentStatus.PAID).build();
        when(paymentRepository.findByGatewayTransactionNo("987654"))
                .thenReturn(Optional.of(alreadyPaid));

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus());
        verify(policyRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_badSignature_unauthorizedNoStateChange() {
        when(paymentGateway.verifyWebhookSignature(any(), any(), any()))
                .thenReturn(PaymentGateway.VerificationResult.invalid("invalid_signature"));

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(401, result.httpStatus());
        assertFalse(result.success());
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_amountMismatch_keepsPendingForManualReview() {
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 100L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success()); // lỗi dữ liệu vẫn 200 để SePay khỏi retry vô ích
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus()); // giữ để đối soát tay
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_unknownContent_noStateChange() {
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSUNKNOWN99")).thenReturn(Optional.empty());
        when(paymentRepository.findByStatus(GatewayPaymentStatus.PENDING)).thenReturn(List.of());

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSUNKNOWN99", "INSUNKNOWN99", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_transferOut_ignored() {
        stubVerifiedWebhook();

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "out", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_accountMismatch_keepsPending() {
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "9999999999", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_policyNotQuoted_keepsPendingForManualReview() {
        quotedPolicy.setStatus(PolicyStatus.ACTIVE);
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByGatewayTransactionNo("987654")).thenReturn(Optional.empty());

        PaymentService.WebhookResult result = webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L));
        assertEquals(200, result.httpStatus());
        assertTrue(result.success());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus()); // giữ để đối soát tay
        verify(policyRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void lookupStatus_readOnlyDoesNotChangeState() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        PaymentResponse res = paymentService.lookupStatus("POL1001-123-ABCDEF", "customer@example.com");
        assertEquals("PENDING", res.getStatus());
        assertEquals("QUOTED", res.getPolicyStatus());
        assertEquals(5_000_000.0, res.getAmount());
        assertEquals("SEPAY", res.getProvider());
        assertEquals("POL1001-123-ABCDEF", res.getTxnRef());
        verify(policyRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void webhook_paymentSaveFails_throwsWithoutCompensationSave() {
        // Track 2 — THAY THẾ test bù trừ cũ (hành vi cũ đã bị thay thế có
        // chủ đích): bù trừ khôi phục QUOTED đã bị XÓA, transaction sẽ
        // rollback policy-save thay cho nó. Ở tầng mock, @Transactional vô
        // tác dụng (bean không qua Spring proxy) nên test này chỉ chứng
        // minh: lỗi gốc lan ra (trigger rollback ở production), KHÔNG có
        // save bù trừ nào (policy chỉ save 1 lần ACTIVE), không mail/ledger.
        // Rollback đa document thật được chứng minh bằng integration test
        // trên replica set (TransactionRollbackIntegrationTest), không phải
        // bằng mock này.
        stubVerifiedWebhook();
        when(paymentRepository.findByTransferContent("INSABCDEF1234"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByGatewayTransactionNo("987654")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new RuntimeException("simulated DB failure on payment save"));
        java.util.List<PolicyStatus> savedStatuses = new java.util.ArrayList<>();
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> {
            savedStatuses.add(inv.getArgument(0, Policy.class).getStatus());
            return inv.getArgument(0);
        });

        assertThrows(RuntimeException.class, () -> webhookOk(webhookBody(
                "987654", "in", "INSABCDEF1234", "INSABCDEF1234", "1234567890", 5_000_000L)));

        // Chỉ 1 lần save ACTIVE, không còn lần save QUOTED bù trừ.
        assertEquals(java.util.List.of(PolicyStatus.ACTIVE), savedStatuses);
        verify(policyRepository, times(1)).save(any(Policy.class));
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
        verify(transactionLedgerService, never()).recordTransaction(any(), any(), any(), any(), any());
    }

    @Test
    void listPayments_includesTransferContentAndScope() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentRepository.findByPolicyNumberOrderByCreatedAtDesc("POL-1001"))
                .thenReturn(List.of(pendingPayment));
        List<PaymentResponse> res = paymentService.listPayments("POL-1001", "customer@example.com");
        assertEquals(1, res.size());
        assertEquals("POL1001-123-ABCDEF", res.get(0).getTxnRef());
        assertEquals("INSABCDEF1234", res.get(0).getTransferContent());
    }

    // FINANCE (PR finance-scope): được tra cứu trạng thái + lịch sử payment
    // (ngữ cảnh tiền, chỉ đọc) dù không phải bên cầu; người lạ vẫn 403.
    @Test
    void lookupStatus_financeAllowed_despiteNotDemandSide() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.hasFinanceRole()).thenReturn(true);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        PaymentResponse res = paymentService.lookupStatus("POL1001-123-ABCDEF", "finance@example.com");
        assertEquals("PENDING", res.getStatus());
        assertEquals("QUOTED", res.getPolicyStatus());
        verify(permissionService, never()).isDemandSide(any(), anyString());
    }

    @Test
    void listPayments_financeAllowed_despiteNotDemandSide() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.hasFinanceRole()).thenReturn(true);
        when(paymentRepository.findByPolicyNumberOrderByCreatedAtDesc("POL-1001"))
                .thenReturn(List.of(pendingPayment));

        List<PaymentResponse> res = paymentService.listPayments("POL-1001", "finance@example.com");
        assertEquals(1, res.size());
        assertEquals("POL1001-123-ABCDEF", res.get(0).getTxnRef());
    }

    @Test
    void lookupStatus_strangerNotDemandSide_throws403() {
        when(permissionService.isAdmin()).thenReturn(false);
        when(permissionService.isUnderwriter()).thenReturn(false);
        when(permissionService.hasFinanceRole()).thenReturn(false);
        when(permissionService.isDemandSide(quotedPolicy, "stranger@example.com")).thenReturn(false);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        assertThrows(ForbiddenException.class,
                () -> paymentService.lookupStatus("POL1001-123-ABCDEF", "stranger@example.com"));
    }
}
