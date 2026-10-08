package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PolicyLocationServiceImpl;
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
class PolicyLocationServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private PolicyLocationServiceImpl policyLocationService;

    private Policy samplePolicy;

    @BeforeEach
    void setUp() {
        Coverage coverage = Coverage.builder()
                .coverageCode("FIRE-01")
                .limit(1000000.0)
                .premium(50000.0)
                .build();

        Location location1 = Location.builder()
                .locationId(1)
                .address("123 Le Loi, Q1")
                .coverages(new ArrayList<>(List.of(coverage)))
                .build();

        samplePolicy = Policy.builder()
                .id("pol-123")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .locations(new ArrayList<>(List.of(location1)))
                .totalPremium(50000.0)
                .build();
    }

    @Test
    void getLocations_Success() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));

        List<Location> locations = policyLocationService.getLocations("pol-123");

        assertNotNull(locations);
        assertEquals(1, locations.size());
        assertEquals("123 Le Loi, Q1", locations.get(0).getAddress());
    }

    @Test
    void getLocationById_Success() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));

        Location location = policyLocationService.getLocationById("pol-123", 1);

        assertNotNull(location);
        assertEquals(1, location.getLocationId());
        assertEquals("123 Le Loi, Q1", location.getAddress());
    }

    @Test
    void getLocationById_NotFound_ThrowsException() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));

        assertThrows(ResourceNotFoundException.class,
                () -> policyLocationService.getLocationById("pol-123", 999));
    }

    @Test
    void addLocation_Success_AutoAssignIdAndRecalculatePremium() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        CoverageDto newCoverage = CoverageDto.builder()
                .coverageCode("THEFT-01")
                .limit(500000.0)
                .premium(25000.0)
                .build();

        LocationDto newLocDto = LocationDto.builder()
                .address("456 Nguyen Hue, Q1")
                .coverages(List.of(newCoverage))
                .build();

        Policy updated = policyLocationService.addLocation("pol-123", newLocDto);

        assertNotNull(updated);
        assertEquals(2, updated.getLocations().size());
        Location added = updated.getLocations().get(1);
        assertEquals(2, added.getLocationId()); // Auto-increment from 1 to 2
        assertEquals("456 Nguyen Hue, Q1", added.getAddress());
        assertEquals(75000.0, updated.getTotalPremium()); // 50000 + 25000
        verify(policyRepository).save(samplePolicy);
    }

    @Test
    void addLocation_DuplicateId_ThrowsException() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));

        LocationDto duplicateDto = LocationDto.builder()
                .locationId(1) // already exists
                .address("Some address")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> policyLocationService.addLocation("pol-123", duplicateDto));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void updateLocation_Success() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        CoverageDto updatedCoverage = CoverageDto.builder()
                .coverageCode("FIRE-01")
                .limit(2000000.0)
                .premium(100000.0)
                .build();

        LocationDto updateDto = LocationDto.builder()
                .address("123 Le Loi (Updated), Q1")
                .coverages(List.of(updatedCoverage))
                .build();

        Policy updated = policyLocationService.updateLocation("pol-123", 1, updateDto);

        assertNotNull(updated);
        Location loc1 = updated.getLocations().get(0);
        assertEquals("123 Le Loi (Updated), Q1", loc1.getAddress());
        assertEquals(100000.0, updated.getTotalPremium());
    }

    @Test
    void removeLocation_Success() {
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy updated = policyLocationService.removeLocation("pol-123", 1);

        assertNotNull(updated);
        assertEquals(0, updated.getLocations().size());
        assertEquals(0.0, updated.getTotalPremium());
        verify(policyRepository).save(samplePolicy);
    }

    @Test
    void modifyLocation_CancelledPolicy_ThrowsException() {
        samplePolicy.setStatus(PolicyStatus.CANCELLED);
        when(policyRepository.findById("pol-123")).thenReturn(Optional.of(samplePolicy));

        LocationDto dto = LocationDto.builder().address("Any address").build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> policyLocationService.addLocation("pol-123", dto));
        assertTrue(ex.getMessage().contains("Cannot modify locations"));
    }
}
