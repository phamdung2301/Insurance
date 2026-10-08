package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private PolicyServiceImpl policyService;

    private Policy samplePolicy;

    @BeforeEach
    void setUp() {
        Insured insured = Insured.builder()
                .name("Global Tech Solutions")
                .email("tech@global.com")
                .build();

        Location location = Location.builder()
                .locationId(1)
                .address("123 Le Loi, District 1")
                .build();

        samplePolicy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .insured(insured)
                .locations(List.of(location))
                .effectiveDate(Instant.now())
                .totalPremium(500000.0)
                .build();
    }

    @Test
    void searchPolicies_WithCombinedFilters_Success() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .status(PolicyStatus.DRAFT)
                .insuredName("Global")
                .location("District 1")
                .effectiveDateFrom(Instant.now().minus(10, ChronoUnit.DAYS))
                .effectiveDateTo(Instant.now().plus(10, ChronoUnit.DAYS))
                .page(0)
                .size(10)
                .sortBy("createdAt")
                .sortDirection("DESC")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Policy.class))).thenReturn(List.of(samplePolicy));

        PageResponse<Policy> response = policyService.searchPolicies(criteria);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals("POL-2026-001", response.getContent().get(0).getPolicyNumber());
        assertEquals(PolicyStatus.DRAFT, response.getContent().get(0).getStatus());
    }

    @Test
    void getPolicyById_FoundById_Success() {
        when(policyRepository.findById("pol-1")).thenReturn(Optional.of(samplePolicy));

        Policy found = policyService.getPolicyById("pol-1");

        assertNotNull(found);
        assertEquals("POL-2026-001", found.getPolicyNumber());
    }

    @Test
    void getPolicyById_FoundByPolicyNumber_Success() {
        when(policyRepository.findById("POL-2026-001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyNumber("POL-2026-001")).thenReturn(Optional.of(samplePolicy));

        Policy found = policyService.getPolicyById("POL-2026-001");

        assertNotNull(found);
        assertEquals("POL-2026-001", found.getPolicyNumber());
    }

    @Test
    void getPolicyById_NotFound_ThrowsException() {
        when(policyRepository.findById("NON_EXISTENT")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyNumber("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> policyService.getPolicyById("NON_EXISTENT"));
    }

    @Test
    void createPolicy_Success() {
        when(policyRepository.existsByPolicyNumber("POL-NEW-01")).thenReturn(false);
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy newPolicy = Policy.builder()
                .policyNumber("POL-NEW-01")
                .build();

        Policy created = policyService.createPolicy(newPolicy);

        assertNotNull(created);
        assertEquals("POL-NEW-01", created.getPolicyNumber());
        assertEquals(PolicyStatus.DRAFT, created.getStatus());
    }

    @Test
    void createPolicy_DuplicatePolicyNumber_ThrowsException() {
        when(policyRepository.existsByPolicyNumber("POL-2026-001")).thenReturn(true);

        Policy duplicate = Policy.builder()
                .policyNumber("POL-2026-001")
                .build();

        assertThrows(BadRequestException.class, () -> policyService.createPolicy(duplicate));
    }
}
