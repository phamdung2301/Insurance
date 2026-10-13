package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.IndexAuditReport;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoIndexAuditServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private MongoIndexAuditService mongoIndexAuditService;

    @BeforeEach
    void setUp() {
        lenient().when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
    }

    @Test
    void ensureOptimizedIndexes_ExecutesEnsureIndexCalls() {
        assertDoesNotThrow(() -> mongoIndexAuditService.ensureOptimizedIndexes());
        verify(indexOperations, atLeast(5)).ensureIndex(any(Index.class));
    }

    @Test
    void auditIndexes_GeneratesReportWithMultikeyAndCompoundClassification() {
        IndexInfo multikeyInfo = new IndexInfo(
                List.of(IndexField.create("locations.coverages.coverageCode", org.springframework.data.domain.Sort.Direction.ASC)),
                "locations_covCode_elem_idx",
                false,
                false,
                ""
        );

        IndexInfo compoundInfo = new IndexInfo(
                List.of(
                        IndexField.create("status", org.springframework.data.domain.Sort.Direction.ASC),
                        IndexField.create("createdAt", org.springframework.data.domain.Sort.Direction.DESC)
                ),
                "status_createdAt_idx",
                false,
                false,
                ""
        );

        when(indexOperations.getIndexInfo()).thenReturn(List.of(multikeyInfo, compoundInfo));

        IndexAuditReport report = mongoIndexAuditService.auditIndexes();

        assertNotNull(report);
        assertEquals("OPTIMIZED", report.getStatus());
        assertTrue(report.getTotalIndexes() > 0);
        assertTrue(report.getRecommendations().size() >= 3);
    }
}
