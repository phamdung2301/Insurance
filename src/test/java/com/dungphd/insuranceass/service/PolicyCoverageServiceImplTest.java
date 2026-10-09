package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PolicyCoverageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyCoverageServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @InjectMocks
    private PolicyCoverageServiceImpl policyCoverageService;

    private Policy samplePolicy;

    @BeforeEach
    void setUp() {
        Coverage cov1 = Coverage.builder()
                .coverageCode("PROP_FIRE")
                .coverageName("Fire Insurance")
                .limit(1000000.0)
                .deductible(5000.0)
                .premium(750.0)
                .build();

        Location loc1 = Location.builder()
                .locationId(1)
                .address("Factory 1, Industrial Zone")
                .coverages(new ArrayList<>(List.of(cov1)))
                .build();

        samplePolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .locations(new ArrayList<>(List.of(loc1)))
                .totalPremium(750.0)
                .build();
    }

    @Test
    void getCoverages_Success() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));

        List<Coverage> coverages = policyCoverageService.getCoverages("pol-1", "1");

        assertNotNull(coverages);
        assertEquals(1, coverages.size());
        assertEquals("PROP_FIRE", coverages.get(0).getCoverageCode());
    }

    @Test
    void getCoverageByCode_Success() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));

        Coverage coverage = policyCoverageService.getCoverageByCode("pol-1", "1", "PROP_FIRE");

        assertNotNull(coverage);
        assertEquals("PROP_FIRE", coverage.getCoverageCode());
        assertEquals(750.0, coverage.getPremium());
    }

    @Test
    void addCoverage_Success_RecalculatesTotalPremium() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));
        when(premiumCalculationService.calculate(any(Coverage.class))).thenReturn(1200.0);
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        CoverageDto newCoverage = CoverageDto.builder()
                .coverageCode("GEN_LIAB")
                .coverageName("General Liability")
                .coverageType("STANDARD")
                .limit(2000000.0)
                .deductible(10000.0)
                .build();

        Policy updated = policyCoverageService.addCoverage("pol-1", "1", newCoverage);

        assertNotNull(updated);
        assertEquals(2, updated.getLocations().get(0).getCoverages().size());
        assertEquals(1950.0, updated.getTotalPremium()); // 750 + 1200
        verify(policyRepository).save(samplePolicy);
    }

    @Test
    void addCoverage_DuplicateCode_ThrowsBadRequest() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));

        CoverageDto duplicate = CoverageDto.builder()
                .coverageCode("PROP_FIRE") // already exists
                .limit(500000.0)
                .build();

        assertThrows(BadRequestException.class, () ->
                policyCoverageService.addCoverage("pol-1", "1", duplicate)
        );
    }

    @Test
    void updateCoverage_Success() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));
        when(premiumCalculationService.calculate(any(Coverage.class))).thenReturn(1000.0);
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        CoverageDto updateDto = CoverageDto.builder()
                .coverageName("Updated Fire Protection")
                .limit(1500000.0)
                .build();

        Policy updated = policyCoverageService.updateCoverage("pol-1", "1", "PROP_FIRE", updateDto);

        assertNotNull(updated);
        Coverage cov = updated.getLocations().get(0).getCoverages().get(0);
        assertEquals("Updated Fire Protection", cov.getCoverageName());
        assertEquals(1500000.0, cov.getLimit());
        assertEquals(1000.0, updated.getTotalPremium());
    }

    @Test
    void removeCoverage_Success() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy updated = policyCoverageService.removeCoverage("pol-1", "1", "PROP_FIRE");

        assertNotNull(updated);
        assertTrue(updated.getLocations().get(0).getCoverages().isEmpty());
        assertEquals(0.0, updated.getTotalPremium());
    }

    @Test
    void removeCoverage_NotFound_ThrowsException() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));

        assertThrows(ResourceNotFoundException.class, () ->
                policyCoverageService.removeCoverage("pol-1", "1", "NON_EXISTENT")
        );
    }
}
