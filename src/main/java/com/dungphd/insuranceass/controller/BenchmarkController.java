package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/benchmark", "/api/benchmark"})
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    /**
     * POST /benchmark/generate-50k
     * FR-BENCH-01: Trigger generation of 50,000 random Policy records in MongoDB
     */
    @PostMapping("/generate-50k")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate50kPolicies(
            @RequestParam(value = "count", defaultValue = "50000") int count
    ) {
        Map<String, Object> result = benchmarkService.generateTestPolicies(count);
        return ResponseEntity.ok(ApiResponse.success(result, "Khởi tạo dữ liệu kiểm thử tải thành công"));
    }

    /**
     * GET /benchmark/run
     * FR-BENCH-02, FR-BENCH-03: Run benchmark for three critical queries and generate metrics report
     */
    @GetMapping("/run")
    public ResponseEntity<ApiResponse<BenchmarkReportDto>> runBenchmark() {
        BenchmarkReportDto report = benchmarkService.runBenchmarkSuite();
        return ResponseEntity.ok(ApiResponse.success(report, "Thực thi kiểm chuẩn hiệu năng truy vấn thành công"));
    }

    /**
     * GET /benchmark/stats
     * Get current collection size and index information
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBenchmarkStats() {
        Map<String, Object> stats = benchmarkService.getCollectionStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats, "Thống kê chỉ mục và dung lượng dữ liệu"));
    }
}
