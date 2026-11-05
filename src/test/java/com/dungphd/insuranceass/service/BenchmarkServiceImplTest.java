package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.service.impl.BenchmarkServiceImpl;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

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
        Agent agent = Agent.builder()
                .id("agent-1")
                .code("AGT-0001")
                .contactEmail("agent1@example.com")
                .status(AgentStatus.ACTIVE)
                .build();
        when(mongoTemplate.find(any(Query.class), eq(Agent.class), eq("agents")))
                .thenReturn(List.of(agent));

        Map<String, Object> res = benchmarkService.generateTestPolicies(10);
        assertNotNull(res);
        assertEquals(10, res.get("insertedCount"));
        assertTrue((long) res.get("elapsedTimeMillis") >= 0);
        assertEquals(1, res.get("agentCount"));
        assertTrue(((List<?>) res.get("agentCodes")).contains("AGT-0001"));
        assertNotNull(res.get("runId"));
        assertTrue(((String) res.get("policyNumberPrefix")).startsWith("POL-BENCH-"));
        verify(mongoTemplate, atLeastOnce()).insert(any(List.class), eq("policies"));
        verify(mongoTemplate, never()).save(any(), eq("agents"));
    }

    @Test
    void generateTestPolicies_NoActiveAgent_CreatesBenchmarkAgent() {
        when(mongoTemplate.find(any(Query.class), eq(Agent.class), eq("agents")))
                .thenReturn(List.of());
        when(mongoTemplate.findOne(any(Query.class), eq(Agent.class), eq("agents")))
                .thenReturn(null);

        Map<String, Object> res = benchmarkService.generateTestPolicies(10);
        assertNotNull(res);
        assertEquals(10, res.get("insertedCount"));
        assertEquals(1, res.get("agentCount"));
        assertTrue(((List<?>) res.get("agentCodes")).contains("AGT-BENCH"));

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(mongoTemplate, times(1)).save(captor.capture(), eq("agents"));
        assertEquals("AGT-BENCH", captor.getValue().getCode());
        assertEquals(AgentStatus.ACTIVE, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getId());
        verify(mongoTemplate, atLeastOnce()).insert(any(List.class), eq("policies"));
    }

    @Test
    void runBenchmarkSuite_Success() {
        when(mongoTemplate.getCollection("policies")).thenReturn(mongoCollection);
        when(mongoCollection.countDocuments()).thenReturn(50000L);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        Agent agent = Agent.builder().id("agent-1").code("AGT-0001").status(AgentStatus.ACTIVE).build();
        when(mongoTemplate.findOne(any(Query.class), eq(Agent.class), eq("agents")))
                .thenReturn(agent);

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
        assertEquals(4, report.getQueryMetrics().size());
        assertEquals("IXSCAN", report.getQueryMetrics().get(0).getWinningPlan());
        assertEquals("IXSCAN", report.getQueryMetrics().get(3).getWinningPlan());
        assertTrue(report.getQueryMetrics().get(3).getQueryFilter().contains("agent-1"));
        assertTrue(report.getCompoundIndexCreated());
    }

    @Test
    void getCollectionStatistics_Success() {
        when(mongoTemplate.getCollection("policies")).thenReturn(mongoCollection);
        when(mongoCollection.countDocuments()).thenReturn(2500L);

        Map<String, Object> stats = benchmarkService.getCollectionStatistics();
        assertNotNull(stats);
        assertEquals(2500L, stats.get("totalPolicies"));
        assertEquals(0L, stats.get("agentCount"));
        assertEquals(0L, stats.get("policiesWithAgent"));
        assertEquals(2500L, stats.get("policiesWithoutAgent"));
    }
}
