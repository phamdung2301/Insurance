package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.PendingEndorsementException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.EndorsementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * Đợt 4B — maker-checker cho endorsement. Ngưỡng mặc định 20%:
 * pct <= 0.20 apply ngay; pct > 0.20 chỉ lưu pending, policy không đổi.
 * PermissionService không có mock ở đây nên null (check write bị bỏ qua
 * như test cũ); quyền thật được test ở PendingEndorsementServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
class PendingEndorsementMakerCheckerTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @Mock
    private PendingEndorsementRepository pendingEndorsementRepository;

    private EndorsementServiceImpl endorsementService;

    private Policy activePolicy;

    @BeforeEach
    void setUp() {
        // KHÔNG dùng @InjectMocks: Mockito chọn constructor injection (4 field
        // final) rồi không field-inject các field @Autowired(required=false)
        // như pendingEndorsementRepository -> repo null -> pending không lưu.
        // Khởi tạo thủ công + ReflectionTestUtils để repo được inject chắc chắn.
        endorsementService = new EndorsementServiceImpl(
                policyRepository, policyVersionRepository, transactionLedgerService, premiumCalculationService);
        ReflectionTestUtils.setField(endorsementService, "pendingEndorsementRepository", pendingEndorsementRepository);
        Coverage cov = Coverage.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000.0)
                .premium(1000.0)
                .build();
        Location loc = Location.builder()
                .locationId(1)
                .address("Warehouse 1, Ha Noi")
                .coverages(new ArrayList<>(List.of(cov)))
                .build();
        activePolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-MC-01")
                .status(PolicyStatus.ACTIVE)
                .version(1)
                .locations(new ArrayList<>(List.of(loc)))
                .totalPremium(1000.0)
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now().plusSeconds(86400 * 365))
                .build();
        // Lenient: không phải test nào cũng dùng hết các stub setUp
        // (vd. test hằng số không gọi repo; test apply không gọi pending save).
        lenient().when(policyRepository.findByPolicyNumber("POL-MC-01")).thenReturn(Optional.of(activePolicy));
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pendingEndorsementRepository.save(any(PendingEndorsement.class)))
                .thenAnswer(inv -> {
                    PendingEndorsement p = inv.getArgument(0);
                    if (p.getId() == null) p.setId("pend-1");
                    return p;
                });
    }

    /** Request thay locations bằng 1 coverage có premium cho trước. */
    private EndorsementRequest requestWithPremium(double premium) {
        CoverageDto cov = CoverageDto.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000.0)
                .premium(premium)
                .build();
        LocationDto loc = LocationDto.builder()
                .locationId(1)
                .address("Warehouse 1, Ha Noi")
                .coverages(List.of(cov))
                .build();
        return EndorsementRequest.builder()
                .reason("Thay doi han muc")
                .locations(List.of(loc))
                .build();
    }

    @Test
    void smallChange_AppliesImmediately() {
        PolicyResponse res = endorsementService.applyEndorsement("POL-MC-01", requestWithPremium(1100.0), "cust@x.com");

        assertNotNull(res);
        assertEquals(2, res.getVersion());
        assertEquals(1100.0, res.getTotalPremium());
        verify(policyRepository).save(any(Policy.class));
        verify(pendingEndorsementRepository, never()).save(any());
        verify(policyVersionRepository).save(any());
        verify(transactionLedgerService).recordTransaction(any(), any(), any(), any(), any());
    }

    @Test
    void exactlyThreshold_AppliesImmediately() {
        // 1000 -> 1200 = +20% đúng biên: vẫn apply (so sánh <= ngưỡng).
        PolicyResponse res = endorsementService.applyEndorsement("POL-MC-01", requestWithPremium(1200.0), "cust@x.com");
        assertEquals(2, res.getVersion());
        assertEquals(1200.0, res.getTotalPremium());
        verify(pendingEndorsementRepository, never()).save(any());
    }

    @Test
    void largeIncrease_CreatesPendingAndLeavesPolicyUnchanged() {
        // 1000 -> 1500 = +50% > 20%.
        PendingEndorsementException ex = assertThrows(PendingEndorsementException.class, () ->
                endorsementService.applyEndorsement("POL-MC-01", requestWithPremium(1500.0), "cust@x.com"));

        assertEquals("pend-1", ex.getPendingId());
        assertEquals(0.5, ex.getPremiumChangePct());
        // Policy KHÔNG đổi: không save policy, không snapshot, không ledger.
        verify(policyRepository, never()).save(any(Policy.class));
        verify(policyVersionRepository, never()).save(any());
        verify(transactionLedgerService, never()).recordTransaction(any(), any(), any(), any(), any());
        assertEquals(1, activePolicy.getVersion());
        assertEquals(1000.0, activePolicy.getTotalPremium());
        // Pending đã lưu, đúng pct + trạng thái PENDING.
        verify(pendingEndorsementRepository).save(argThat(p ->
                p.getStatus() == PendingEndorsementStatus.PENDING
                        && p.getPolicyNumber().equals("POL-MC-01")
                        && p.getPremiumChangePct() == 0.5
                        && p.getRequestedBy().equals("cust@x.com")
                        && p.getRequest() != null
                        && p.getRequest().getReason().equals("Thay doi han muc")));
    }

    @Test
    void largeDecrease_CreatesPendingToo() {
        // 1000 -> 700 = -30%, trị tuyệt đối 30% > 20%.
        assertThrows(PendingEndorsementException.class, () ->
                endorsementService.applyEndorsement("POL-MC-01", requestWithPremium(700.0), "cust@x.com"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(pendingEndorsementRepository).save(argThat(p -> p.getPremiumChangePct() == 0.3));
    }

    @Test
    void oldPremiumZero_NewPremiumNonZero_CreatesPending() {
        activePolicy.setTotalPremium(0.0);
        PendingEndorsementException ex = assertThrows(PendingEndorsementException.class, () ->
                endorsementService.applyEndorsement("POL-MC-01", requestWithPremium(500.0), "cust@x.com"));
        // Quy ước phí cũ = 0, phí mới != 0: pct lưu 1.0 (vượt ngưỡng).
        assertEquals(1.0, ex.getPremiumChangePct());
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void thresholdConstant_IsTwentyPercent() {
        assertEquals(0.20, EndorsementServiceImpl.PREMIUM_CHANGE_THRESHOLD);
    }
}
