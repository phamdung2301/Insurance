package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.IndexAuditReport;
import com.dungphd.insuranceass.service.MongoIndexAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/admin/indexes", "/api/admin/indexes"})
@RequiredArgsConstructor
public class IndexAuditController {

    private final MongoIndexAuditService mongoIndexAuditService;

    /**
     * GET /api/admin/indexes/audit
     * Audit all collection indexes, detecting multikey $elemMatch, compound indexes, and redundancies.
     */
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<IndexAuditReport>> auditIndexes() {
        IndexAuditReport report = mongoIndexAuditService.auditIndexes();
        return ResponseEntity.ok(ApiResponse.success(report, "MongoDB index audit completed successfully"));
    }

    /**
     * POST /api/admin/indexes/sync
     * Synchronize and ensure all optimized indexes in MongoDB.
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<String>> syncIndexes() {
        mongoIndexAuditService.ensureOptimizedIndexes();
        return ResponseEntity.ok(ApiResponse.success("All optimized compound and $elemMatch multikey indexes synchronized.", "Index sync successful"));
    }
}
