package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkReportDto {

    private Long totalCollectionRecords;
    private Boolean compoundIndexCreated;
    private List<BenchmarkQueryMetricDto> queryMetrics;
    private Double averageExecutionTimeMillis;
    private String benchmarkSummary;
    private String timestamp;
}
