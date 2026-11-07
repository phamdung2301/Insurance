package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.exception.PendingEndorsementException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PendingEndorsementRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.EndorsementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EndorsementNotificationTriggerTest {

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
    @Mock
    private PermissionService permissionService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EndorsementServiceImpl endorsementService;

    private Policy activePolicy;

    @BeforeEach
    void setUp() {
        Coverage cov = Coverage.builder().coverageCode("PROP_FIRE").limit(1000000.0).premium(750.0).build();
        Location loc = Location.builder().locationId(1).address("Warehouse 1, Ha Noi")
                .coverages(new ArrayList<>(List.of(cov))).build();
        activePolicy = Policy.builder()
                .id("pol-1").policyNumber("POL-ACTIVE-01").status(PolicyStatus.ACTIVE).version(1)
                .locations(new ArrayList<>(List.of(loc))).totalPremium(750.0)
                .effectiveDate(Instant.now()).expirationDate(Instant.now().plusSeconds(86400 * 365))
                .build();
        lenient().when(policyRepository.findByPolicyNumber("POL-ACTIVE-01")).thenReturn(Optional.of(activePolicy));
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pendingEndorsementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Mockito @InjectMocks chỉ inject qua constructor nên các field
        // @Autowired(required=false) vẫn null — inject tường minh tại đây.
        ReflectionTestUtils.setField(endorsementService, "permissionService", permissionService);
        ReflectionTestUtils.setField(endorsementService, "notificationService", notificationService);
        ReflectionTestUtils.setField(endorsementService, "pendingEndorsementRepository", pendingEndorsementRepository);
        // Giá server-authoritative: mock echo premium có sẵn trên entity —
        // test này kiểm tra trigger notification, không test công thức giá.
        lenient().when(premiumCalculationService.calculateServerPriced(any(Coverage.class)))
                .thenAnswer(inv -> {
                    Coverage c = inv.getArgument(0);
                    return c != null && c.getPremium() != null ? c.getPremium() : 0.0;
                });
        lenient().when(premiumCalculationService.resolveServerBaseRate(any())).thenReturn(0.1);
    }

    private EndorsementRequest requestWithNewCoverage(double newPremium) {
        CoverageDto newCoverage = CoverageDto.builder()
                .coverageCode("GEN_LIAB").limit(2000000.0).premium(newPremium).build();
        LocationDto newLoc = LocationDto.builder().locationId(2).address("Factory 2, Hai Phong")
                .coverages(List.of(newCoverage)).build();
        return EndorsementRequest.builder()
                .reason("Mở rộng xưởng sản xuất Hải Phòng")
                .locations(List.of(LocationDto.fromEntity(activePolicy.getLocations().get(0)), newLoc))
                .build();
    }

    @Test
    void directApply_sendsRequestedMailOnce_andAppliedMailOnce() {
        // 750 -> 900 = +20% đúng biên ngưỡng nên apply ngay.
        endorsementService.applyEndorsement("POL-ACTIVE-01", requestWithNewCoverage(150.0),
                "customer@example.com");
        verify(notificationService, times(1)).notifyEndorsementApplied(any());
        verify(notificationService, times(1)).notifyEndorsementRequested(
                any(), any(), eq("customer@example.com"),
                isNull(), isNull(), isNull(), isNull(), eq(true));
    }

    @Test
    void pendingBranch_sendsRequestedMailBeforeThrowing202() {
        // 750 -> 1750 = +133% > 20% nên pending; mail phải gửi TRƯỚC exception.
        assertThrows(PendingEndorsementException.class, () ->
                endorsementService.applyEndorsement("POL-ACTIVE-01", requestWithNewCoverage(1000.0),
                        "customer@example.com"));
        verify(notificationService, times(1)).notifyEndorsementRequested(
                any(), any(), eq("customer@example.com"),
                isNull(), anyDouble(), eq(750.0), eq(1750.0), eq(false));
        verify(notificationService, never()).notifyEndorsementApplied(any());
    }

    @Test
    void checkerApprove_bypassDoesNotResendRequestedMail() {
        endorsementService.applyEndorsementBypassingThreshold("POL-ACTIVE-01",
                requestWithNewCoverage(1000.0), "uw@example.com");
        verify(notificationService, times(1)).notifyEndorsementApplied(any());
        verify(notificationService, never()).notifyEndorsementRequested(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }
}
