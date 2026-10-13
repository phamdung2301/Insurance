package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexAuditReport {

    private String status;
    private Instant auditTimestamp;
    private int totalCollectionsAudited;
    private int totalIndexes;
    private int totalMultikeyIndexes;
    private int totalCompoundIndexes;
    private int redundantIndexesDetected;
    private List<CollectionIndexSummary> collections;
    private List<String> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionIndexSummary {
        private String collectionName;
        private int indexCount;
        private List<IndexDetail> indexes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexDetail {
        private String name;
        private Map<String, Object> keyPattern;
        private boolean unique;
        private boolean sparse;
        private boolean isCompound;
        private boolean isMultikeyElemMatch;
        private String purpose;
        private String status; // "ACTIVE_OPTIMIZED", "REDUNDANT", "MISSING"
    }
}
