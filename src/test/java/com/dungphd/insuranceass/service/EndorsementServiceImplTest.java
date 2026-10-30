package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.EndorsementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndorsementServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    // Đợt 4B: bean pending (mock) để luồng maker-checker có repo; case thành
    // công dưới đây giữ ở đúng biên ngưỡng 20% nên không chạm tới repo này.
    @Mock
    private com.dungphd.insuranceass.repository.PendingEndorsementRepository pendingEndorsementRepository;

    @InjectMocks
    private EndorsementServiceImpl endorsementService;

    private Policy activePolicy;

    @BeforeEach
    void setUp() {
        Coverage cov = Coverage.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000.0)
                .premium(750.0)
                .build();

        Location loc = Location.builder()
                .locationId(1)
                .address("Warehouse 1, Ha Noi")
                .coverages(new ArrayList<>(List.of(cov)))
                .build();

        activePolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-ACTIVE-01")
                .status(PolicyStatus.ACTIVE)
                .version(1)
                .locations(new ArrayList<>(List.of(loc)))
                .totalPremium(750.0)
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now().plusSeconds(86400 * 365))
                .build();
    }

    @Test
    void applyEndorsement_Success_CreatesSnapshotAndIncrementsVersion() {
        when(policyRepository.findByPolicyNumber("POL-ACTIVE-01")).thenReturn(Optional.of(activePolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        // Đợt 4B: 750 -> 900 = +20% đúng biên ngưỡng nên vẫn apply ngay.
        // (Premium cũ 1200 cho tổng 1950 = +160% sẽ đi pending theo luật mới,
        // xem PendingEndorsementMakerCheckerTest.)
        CoverageDto newCoverage = CoverageDto.builder()
                .coverageCode("GEN_LIAB")
                .limit(2000000.0)
                .premium(150.0)
                .build();

        LocationDto newLoc = LocationDto.builder()
                .locationId(2)
                .address("Factory 2, Hai Phong")
                .coverages(List.of(newCoverage))
                .build();

        EndorsementRequest request = EndorsementRequest.builder()
                .reason("Mở rộng xưởng sản xuất Hải Phòng")
                .locations(List.of(
                        LocationDto.fromEntity(activePolicy.getLocations().get(0)),
                        newLoc
                ))
                .build();

        PolicyResponse res = endorsementService.applyEndorsement("POL-ACTIVE-01", request, "underwriter@insurance.com");

        assertNotNull(res);
        assertEquals(2, res.getVersion());
        assertEquals(2, res.getLocations().size());
        assertEquals(900.0, res.getTotalPremium()); // 750 + 150

        // Verify version snapshot 1 was saved into policy_versions
        verify(policyVersionRepository).save(argThat(v ->
                v.getVersion().equals(1) && v.getPolicyNumber().equals("POL-ACTIVE-01")
        ));

        // Verify transaction logged in Ledger
        verify(transactionLedgerService).recordTransaction(
                eq("POL-ACTIVE-01"),
                eq(2),
                eq(TransactionType.ENDORSEMENT),
                eq("underwriter@insurance.com"),
                contains("Mở rộng xưởng")
        );
    }

    @Test
    void applyEndorsement_DraftPolicy_ThrowsBadRequest() {
        activePolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findByPolicyNumber("POL-ACTIVE-01")).thenReturn(Optional.of(activePolicy));

        EndorsementRequest request = EndorsementRequest.builder()
                .reason("Test")
                .build();

        assertThrows(BadRequestException.class, () ->
                endorsementService.applyEndorsement("POL-ACTIVE-01", request, "Admin")
        );
    }

    @Test
    void getVersions_Success() {
        when(policyRepository.findByPolicyNumber("POL-ACTIVE-01")).thenReturn(Optional.of(activePolicy));

        PolicyVersion pv1 = PolicyVersion.builder()
                .policyNumber("POL-ACTIVE-01")
                .version(1)
                .build();

        when(policyVersionRepository.findByPolicyNumberOrderByVersionDesc("POL-ACTIVE-01"))
                .thenReturn(List.of(pv1));

        List<PolicyVersion> versions = endorsementService.getVersions("POL-ACTIVE-01");

        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersion());
    }

    @Test
    void compareVersions_Success() {
        activePolicy.setVersion(2);
        when(policyRepository.findByPolicyNumber("POL-ACTIVE-01")).thenReturn(Optional.of(activePolicy));

        Policy snap1 = Policy.builder()
                .policyNumber("POL-ACTIVE-01")
                .version(1)
                .totalPremium(500.0)
                .locations(new ArrayList<>())
                .build();

        PolicyVersion pv1 = PolicyVersion.builder()
                .policyNumber("POL-ACTIVE-01")
                .version(1)
                .policySnapshot(snap1)
                .build();

        when(policyVersionRepository.findByPolicyNumberAndVersion("POL-ACTIVE-01", 1))
                .thenReturn(Optional.of(pv1));

        Map<String, Object> diff = endorsementService.compareVersions("POL-ACTIVE-01", 1, 2);

        assertNotNull(diff);
        assertEquals(1, diff.get("version1"));
        assertEquals(2, diff.get("version2"));
        assertEquals(500.0, diff.get("totalPremium1"));
        assertEquals(750.0, diff.get("totalPremium2"));
        assertEquals(250.0, diff.get("premiumDifference"));
    }
}
