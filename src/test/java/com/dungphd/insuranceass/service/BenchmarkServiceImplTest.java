package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.service.impl.BenchmarkServiceImpl;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoCollection<Document> mongoCollection;

    private BenchmarkServiceImpl benchmarkService;

    @BeforeEach
    void setUp() {
        benchmarkService = new BenchmarkServiceImpl(mongoTemplate);
    }

    @Test
    void generateTestPolicies_Success() {
        Map<String, Object> res = benchmarkService.generateTestPolicies(10);
        assertNotNull(res);
        assertEquals(10, res.get("insertedCount"));
        assertTrue((long) res.get("elapsedTimeMillis") >= 0);
        verify(mongoTemplate, atLeastOnce()).insert(any(List.class), eq("policies"));
    }

    @Test
    void runBenchmarkSuite_Success() {
        when(mongoTemplate.getCollection("policies")).thenReturn(mongoCollection);
        when(mongoCollection.countDocuments()).thenReturn(50000L);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);

        Document executionStats = new Document("executionTimeMillis", 12L)
                .append("totalDocsExamined", 15000L)
                .append("totalKeysExamined", 15000L)
                .append("nReturned", 15000L)
                .append("executionStages", new Document("stage", "IXSCAN"));

        Document explainRes = new Document("executionStats", executionStats);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(explainRes);

        BenchmarkReportDto report = benchmarkService.runBenchmarkSuite();
        assertNotNull(report);
        assertEquals(50000L, report.getTotalCollectionRecords());
        assertEquals(3, report.getQueryMetrics().size());
        assertEquals("IXSCAN", report.getQueryMetrics().get(0).getWinningPlan());
        assertTrue(report.getCompoundIndexCreated());
    }

    @Test
    void getCollectionStatistics_Success() {
        when(mongoTemplate.getCollection("policies")).thenReturn(mongoCollection);
        when(mongoCollection.countDocuments()).thenReturn(2500L);

        Map<String, Object> stats = benchmarkService.getCollectionStatistics();
        assertNotNull(stats);
        assertEquals(2500L, stats.get("totalPolicies"));
    }
}
