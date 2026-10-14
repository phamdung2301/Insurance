package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;

import java.util.Map;

public interface BenchmarkService {

    /**
     * FR-BENCH-01: Generate test Policy records conforming to the MongoDB schema in batches.
     */
    Map<String, Object> generateTestPolicies(int targetCount);

    /**
     * FR-BENCH-02, FR-BENCH-03: Execute benchmark for 3 critical queries, extracting execution metrics:
     * winningPlan, totalDocsExamined, totalKeysExamined, nReturned, executionTimeMillis.
     */
    BenchmarkReportDto runBenchmarkSuite();

    /**
     * Get current policy collection statistics.
     */
    Map<String, Object> getCollectionStatistics();
}
