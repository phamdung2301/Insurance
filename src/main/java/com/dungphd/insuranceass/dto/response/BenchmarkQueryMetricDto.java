package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkQueryMetricDto {

    private String queryName;
    private String queryFilter;
    private Long executionTimeMillis;
    private Long totalDocsExamined;
    private Long totalKeysExamined;
    private Long nReturned;
    private String winningPlan; // e.g. "IXSCAN" or "COLLSCAN"
    private Boolean indexUsed;
    private String optimizationStatus; // e.g. "OPTIMAL (Index Scan)" or "UNINDEXED (Collection Scan)"
}
