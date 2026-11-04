package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.BenchmarkReportDto;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/benchmark", "/api/benchmark"})
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    /**
     * Cờ tắt/mở benchmark (đợt 1C, L2 gate).
     * Mặc định FALSE: mọi endpoint trong controller này đều trả 403 khi cờ tắt.
     * CHỈ bật ở môi trường dev (app.benchmark.enabled=true); production luôn tắt,
     * vì generate-50k ghi 50.000 bản ghi thật vào DB production nếu bị gọi nhầm.
     * Không sửa application.properties (file user đang sửa local): cờ có default
     * trong code qua @Value nên thiếu property vẫn chạy an toàn (tắt).
     */
    @Value("${app.benchmark.enabled:false}")
    private boolean benchmarkEnabled;

    private void requireBenchmarkEnabled() {
        if (!benchmarkEnabled) {
            throw new ForbiddenException("Benchmark đã bị tắt.");
        }
    }

    /**
     * POST /benchmark/generate-50k
     * FR-BENCH-01: Trigger generation of 50,000 random Policy records in MongoDB
     */
    @PostMapping({"/generate-50k", "/generate"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate50kPolicies(
            @RequestParam(value = "count", defaultValue = "50000") int count
    ) {
        requireBenchmarkEnabled();
        Map<String, Object> result = benchmarkService.generateTestPolicies(count);
        return ResponseEntity.ok(ApiResponse.success(result, "Khởi tạo dữ liệu kiểm thử tải thành công"));
    }

    /**
     * GET /benchmark/run, GET /benchmark/report
     * FR-BENCH-02, FR-BENCH-03: Run benchmark for three critical queries and generate metrics report
     */
    @GetMapping({"/run", "/report"})
    public ResponseEntity<ApiResponse<BenchmarkReportDto>> runBenchmark() {
        requireBenchmarkEnabled();
        BenchmarkReportDto report = benchmarkService.runBenchmarkSuite();
        return ResponseEntity.ok(ApiResponse.success(report, "Thực thi kiểm chuẩn hiệu năng truy vấn thành công"));
    }

    /**
     * GET /benchmark/stats
     * Get current collection size and index information
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBenchmarkStats() {
        requireBenchmarkEnabled();
        Map<String, Object> stats = benchmarkService.getCollectionStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats, "Thống kê chỉ mục và dung lượng dữ liệu"));
    }
}
