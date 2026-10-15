package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.MonthlyPremiumReportDto;
import com.dungphd.insuranceass.dto.response.PolicyStatusCountReportDto;
import com.dungphd.insuranceass.dto.response.StatusPremiumReportDto;
import com.dungphd.insuranceass.dto.response.TopPolicyReportDto;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.impl.ReportServiceImpl;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AggregationResults<Document> docResults;

    @Mock
    private AggregationResults<Policy> policyResults;

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(mongoTemplate);
    }

    @Test
    void getPolicyCountByStatus_Success() {
        Document d1 = new Document("status", "ACTIVE").append("count", 15L);
        Document d2 = new Document("status", "DRAFT").append("count", 5L);

        when(docResults.getMappedResults()).thenReturn(List.of(d1, d2));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("policies"), eq(Document.class)))
                .thenReturn(docResults);

        List<PolicyStatusCountReportDto> res = reportService.getPolicyCountByStatus();
        assertNotNull(res);
        assertEquals(2, res.size());
        assertEquals("ACTIVE", res.get(0).getStatus());
        assertEquals(15L, res.get(0).getCount());
        assertEquals(75.0, res.get(0).getPercentage());
    }

    @Test
    void getTotalPremiumByStatus_Success() {
        Document d1 = new Document("status", "ACTIVE")
                .append("totalPremium", 50000000.0)
                .append("policyCount", 10L)
                .append("averagePremium", 5000000.0);

        when(docResults.getMappedResults()).thenReturn(List.of(d1));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("policies"), eq(Document.class)))
                .thenReturn(docResults);

        List<StatusPremiumReportDto> res = reportService.getTotalPremiumByStatus();
        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(50000000.0, res.get(0).getTotalPremium());
    }

    @Test
    void getTop5HighestPremiumPolicies_Success() {
        Policy p = Policy.builder()
                .policyNumber("POL-TOP-1")
                .status(PolicyStatus.ACTIVE)
                .totalPremium(99000000.0)
                .insured(Insured.builder().name("Tập đoàn Top 1").build())
                .effectiveDate(Instant.now())
                .build();

        when(policyResults.getMappedResults()).thenReturn(List.of(p));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("policies"), eq(Policy.class)))
                .thenReturn(policyResults);

        List<TopPolicyReportDto> res = reportService.getTop5HighestPremiumPolicies();
        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals("POL-TOP-1", res.get(0).getPolicyNumber());
        assertEquals(99000000.0, res.get(0).getTotalPremium());
    }

    @Test
    void getPremiumByEffectiveMonth_Success() {
        Document idDoc = new Document("year", 2026).append("month", 10);
        Document doc = new Document("_id", idDoc)
                .append("totalPremium", 120000000.0)
                .append("policyCount", 24L);

        when(docResults.getMappedResults()).thenReturn(List.of(doc));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("policies"), eq(Document.class)))
                .thenReturn(docResults);

        List<MonthlyPremiumReportDto> res = reportService.getPremiumByEffectiveMonth();
        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals(10, res.get(0).getMonth());
        assertEquals("T10/2026", res.get(0).getMonthLabel());
        assertEquals(120000000.0, res.get(0).getTotalPremium());
    }
}
