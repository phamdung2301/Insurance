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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Policy quotedPolicy;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
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
                .provider("VNPAY")
                .txnRef("POL1001-123-ABCDEF")
                .status(GatewayPaymentStatus.PENDING)
                .build();
        lenient().when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(quotedPolicy));
        lenient().when(policyRepository.findById("POL-1001")).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Map<String, String> ipnParams(String responseCode, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "POL1001-123-ABCDEF");
        params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionNo", "14012345");
        params.put("vnp_PayDate", "20261104120000");
        params.put("vnp_SecureHash", "dummy");
        return params;
    }

    @Test
    void createPayment_quotedAndDemandSide_returnsUrlAndPending() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentGateway.buildPaymentUrl(anyString(), eq(5_000_000L), anyString(), eq("127.0.0.1")))
                .thenReturn("https://sandbox.vnpayment.vn/pay?x=1");

        PaymentResponse res = paymentService.createPayment("POL-1001", "customer@example.com", "127.0.0.1");
        assertEquals("PENDING", res.getStatus());
        assertEquals("https://sandbox.vnpayment.vn/pay?x=1", res.getPaymentUrl());
        assertEquals(5_000_000.0, res.getAmount());
        assertNotNull(res.getTxnRef());
    }

    @Test
    void createPayment_supersedesOldPending() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(true);
        Payment old = Payment.builder().policyNumber("POL-1001").status(GatewayPaymentStatus.PENDING).build();
        when(paymentRepository.findByPolicyNumberAndStatus("POL-1001", GatewayPaymentStatus.PENDING))
                .thenReturn(List.of(old));
        when(paymentGateway.buildPaymentUrl(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn("https://sandbox/pay");

        paymentService.createPayment("POL-1001", "customer@example.com", "127.0.0.1");
        assertEquals(GatewayPaymentStatus.CANCELLED, old.getStatus());
    }

    @Test
    void createPayment_notDemandSide_forbidden() {
        when(permissionService.isDemandSide(quotedPolicy, "other@example.com")).thenReturn(false);
        assertThrows(ForbiddenException.class, () ->
                paymentService.createPayment("POL-1001", "other@example.com", "127.0.0.1"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_notQuoted_conflict() {
        quotedPolicy.setStatus(PolicyStatus.BOUND);
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com", "127.0.0.1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createPayment_gatewayNotConfigured_serviceUnavailable() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentGateway.isConfigured()).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com", "127.0.0.1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void createPayment_negotiatedMismatch_rejected() {
        quotedPolicy.setNegotiatedPremium(4_000_000.0);
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        assertThrows(Exception.class, () ->
                paymentService.createPayment("POL-1001", "customer@example.com", "127.0.0.1"));
    }

    @Test
    void ipn_validSignatureAndAmount_activatesOnceAndNotifies() {
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentGateway.parseMinorAmountToVnd("500000000")).thenReturn(5_000_000L);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "500000000"));
        assertEquals("00", rsp.get("RspCode"));
        assertEquals(PolicyStatus.ACTIVE, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("14012345", pendingPayment.getGatewayTransactionNo());
        assertNotNull(pendingPayment.getPaidAt());
        assertNotNull(quotedPolicy.getEffectiveDate());
        assertNotNull(quotedPolicy.getExpirationDate());
        verify(notificationService, times(1)).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void ipn_duplicateWhenPaid_idempotentNoMailAgain() {
        pendingPayment.setStatus(GatewayPaymentStatus.PAID);
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "500000000"));
        assertEquals("00", rsp.get("RspCode"));
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus()); // không chuyển đổi lại
        verify(policyRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void ipn_badSignature_rejectedNoStateChange() {
        when(paymentGateway.verifySignature(any()))
                .thenReturn(PaymentGateway.VerificationResult.invalid("invalid_signature"));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "500000000"));
        assertEquals("97", rsp.get("RspCode"));
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void ipn_amountMismatch_rejectedNoStateChange() {
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentGateway.parseMinorAmountToVnd("100")).thenReturn(1L);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "100"));
        assertEquals("04", rsp.get("RspCode"));
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus());
        assertEquals(GatewayPaymentStatus.PENDING, pendingPayment.getStatus()); // giữ để đối soát tay
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void ipn_unknownTxnRef_rejected() {
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.empty());

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "500000000"));
        assertEquals("01", rsp.get("RspCode"));
    }

    @Test
    void ipn_policyAlreadyActive_guardNoTransition() {
        quotedPolicy.setStatus(PolicyStatus.ACTIVE);
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentGateway.parseMinorAmountToVnd("500000000")).thenReturn(5_000_000L);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("00", "500000000"));
        assertEquals("99", rsp.get("RspCode"));
        verify(policyRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void ipn_failedResponseCode_marksFailedKeepsQuoted() {
        when(paymentGateway.verifySignature(any())).thenReturn(PaymentGateway.VerificationResult.ok());
        when(paymentGateway.parseMinorAmountToVnd("500000000")).thenReturn(5_000_000L);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));

        Map<String, String> rsp = paymentService.handleIpn(ipnParams("24", "500000000"));
        assertEquals("00", rsp.get("RspCode")); // đã nhận IPN, nhưng payment CANCELLED
        assertEquals(GatewayPaymentStatus.CANCELLED, pendingPayment.getStatus());
        assertEquals(PolicyStatus.QUOTED, quotedPolicy.getStatus());
        verify(notificationService, never()).notifyPaymentSucceeded(any(), any());
    }

    @Test
    void lookupReturn_badSignature_forbiddenAndReadOnly() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));
        Map<String, String> params = ipnParams("00", "500000000");
        when(paymentGateway.verifySignature(params))
                .thenReturn(PaymentGateway.VerificationResult.invalid("invalid_signature"));

        assertThrows(ForbiddenException.class, () ->
                paymentService.lookupReturn("POL1001-123-ABCDEF", params, "customer@example.com"));
        verify(policyRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void lookupReturn_validSignature_doesNotChangeState() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentRepository.findByTxnRef("POL1001-123-ABCDEF")).thenReturn(Optional.of(pendingPayment));
        Map<String, String> params = ipnParams("00", "500000000");
        when(paymentGateway.verifySignature(params)).thenReturn(PaymentGateway.VerificationResult.ok());

        PaymentResponse res = paymentService.lookupReturn("POL1001-123-ABCDEF", params, "customer@example.com");
        assertEquals("PENDING", res.getStatus());
        assertEquals("QUOTED", res.getPolicyStatus());
        assertTrue(res.getSignatureValid());
        verify(policyRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void listPayments_scopeDemandSide() {
        when(permissionService.isDemandSide(quotedPolicy, "customer@example.com")).thenReturn(true);
        when(paymentRepository.findByPolicyNumberOrderByCreatedAtDesc("POL-1001"))
                .thenReturn(List.of(pendingPayment));
        List<PaymentResponse> res = paymentService.listPayments("POL-1001", "customer@example.com");
        assertEquals(1, res.size());
        assertEquals("POL1001-123-ABCDEF", res.get(0).getTxnRef());
    }
}
