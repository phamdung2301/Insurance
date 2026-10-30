package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.model.RefundRecord;
import com.dungphd.insuranceass.service.FinanceReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Đối soát cho FINANCE (đợt 3C, việc 3).
 * Chặn 2 lớp: matcher "/finance/**", "/api/finance/**" →
 * hasAnyRole("FINANCE", "ADMIN") trong SecurityConfig + kiểm tra lại
 * trong FinanceReconciliationService (chỉ FINANCE/ADMIN, fail-closed).
 */
@RestController
@RequestMapping({"/finance", "/api/finance"})
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceReconciliationService financeReconciliationService;

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<RefundRecord>>> listRefunds() {
        return ResponseEntity.ok(ApiResponse.success(
                financeReconciliationService.listRefunds(), "Danh sách hoàn phí chờ đối soát."));
    }

    @GetMapping("/reconciliation/export")
    public ResponseEntity<byte[]> exportReconciliation(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = financeReconciliationService.exportReconciliationCsv(from, to);
        String filename = "DoiSoat_" + (from != null ? from : "all") + "_to_" + (to != null ? to : "all") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
