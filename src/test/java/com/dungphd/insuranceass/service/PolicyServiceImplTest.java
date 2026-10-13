package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTest {

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

    @InjectMocks
    private PolicyServiceImpl policyService;

    @Test
    void searchPolicies_ValidSortBy_Success() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .page(0)
                .size(10)
                .sortBy("totalPremium")
                .sortDirection("DESC")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Policy.class)))
                .thenReturn(List.of(Policy.builder().policyNumber("POL-1").build()));

        PageResponse<Policy> result = policyService.searchPolicies(criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchPolicies_InvalidSortBy_ThrowsBadRequestException() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .sortBy("invalidFieldInjection")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> policyService.searchPolicies(criteria));

        assertTrue(ex.getMessage().contains("Invalid sortBy field: 'invalidFieldInjection'"));
    }

    @Test
    void searchPolicies_SizeExceedingMax_ClampedTo100() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .page(0)
                .size(500) // Exceeds MAX_PAGE_SIZE (100)
                .sortBy("createdAt")
                .build();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(0L);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Policy.class)))
                .thenReturn(Collections.emptyList());

        PageResponse<Policy> result = policyService.searchPolicies(criteria);

        assertNotNull(result);
        Query capturedQuery = queryCaptor.getValue();
        // Verifies the query's page size is clamped to MAX_PAGE_SIZE (100)
        assertNotNull(capturedQuery);
    }

    @Test
    void searchPolicies_WithCoverageCode_BuildsElemMatchCriteria() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .coverageCode("FIRE-01")
                .build();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(1L);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Policy.class)))
                .thenReturn(List.of(Policy.builder().policyNumber("POL-ELEM-1").build()));

        PageResponse<Policy> result = policyService.searchPolicies(criteria);

        assertNotNull(result);
        Query capturedQuery = queryCaptor.getValue();
        String queryStr = capturedQuery.getQueryObject().toJson();
        assertTrue(queryStr.contains("$elemMatch"));
        assertTrue(queryStr.contains("FIRE-01"));
    }

    @Test
    void searchPolicies_WithCoverageCodeAndType_BuildsNestedElemMatchCriteria() {
        PolicySearchCriteria criteria = PolicySearchCriteria.builder()
                .coverageCode("FIRE-01")
                .coverageType("STANDARD")
                .build();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(1L);
        when(mongoTemplate.find(queryCaptor.capture(), eq(Policy.class)))
                .thenReturn(List.of(Policy.builder().policyNumber("POL-ELEM-2").build()));

        PageResponse<Policy> result = policyService.searchPolicies(criteria);

        assertNotNull(result);
        Query capturedQuery = queryCaptor.getValue();
        String queryStr = capturedQuery.getQueryObject().toJson();
        assertTrue(queryStr.contains("$elemMatch"));
        assertTrue(queryStr.contains("FIRE-01"));
        assertTrue(queryStr.contains("STANDARD"));
    }
}
