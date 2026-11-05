package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.StatusTransitionRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.model.PaymentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Đợt transaction/ACID: ledger ghi SAU khi policy đã save thì fail của nó
 * không được biến transition đã commit thành 500.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTransitionLedgerTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PriceNegotiationRepository negotiationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private PolicyServiceImpl policyService;

    @BeforeEach
    void setUp() {
        // Các field @Autowired(required = false) của PolicyServiceImpl không
        // được @InjectMocks inject đáng tin cậy trong mọi phiên bản Mockito,
        // nên set tường minh bằng ReflectionTestUtils.
        ReflectionTestUtils.setField(policyService, "permissionService", permissionService);
        ReflectionTestUtils.setField(policyService, "negotiationRepository", negotiationRepository);
        ReflectionTestUtils.setField(policyService, "notificationService", notificationService);
        ReflectionTestUtils.setField(policyService, "refundService", refundService);
    }

    @Test
    void transitionStatus_LedgerFails_StillReturnsActive() {
        Policy bound = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-BOUND-01")
                .status(PolicyStatus.BOUND)
                .version(1)
                .effectiveDate(Instant.now())
                .paymentStatus(PaymentStatus.CLAIMED)
                .build();
        when(policyRepository.findByPolicyNumber("POL-BOUND-01")).thenReturn(Optional.of(bound));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("simulated ledger DB failure"))
                .when(transactionLedgerService).recordTransaction(any(), any(), any(), any(), any());

        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .targetStatus(PolicyStatus.ACTIVE)
                .reason("Finance xác nhận thu tiền")
                .build();

        PolicyResponse res = policyService.transitionStatus("POL-BOUND-01", request, "finance@x.com");

        assertNotNull(res);
        assertEquals(PolicyStatus.ACTIVE, res.getStatus());
        assertEquals(PaymentStatus.CONFIRMED, bound.getPaymentStatus());
        verify(policyRepository).save(any(Policy.class));
        // Hook sau save vẫn chạy (không bị ledger fail chặn trước đó).
        verify(notificationService).notifyPolicyActivated(any(Policy.class));
    }
}
