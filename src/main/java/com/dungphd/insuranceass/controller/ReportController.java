package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.MonthlyPremiumReportDto;
import com.dungphd.insuranceass.dto.response.PolicyStatusCountReportDto;
import com.dungphd.insuranceass.dto.response.StatusPremiumReportDto;
import com.dungphd.insuranceass.dto.response.TopPolicyReportDto;
import com.dungphd.insuranceass.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/reports", "/api/reports"})
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /reports/policy-count-by-status
     * FR-REP-01: Groups policies by status and counts total records in each group
     */
    @GetMapping("/policy-count-by-status")
    public ResponseEntity<ApiResponse<List<PolicyStatusCountReportDto>>> getPolicyCountByStatus() {
        List<PolicyStatusCountReportDto> report = reportService.getPolicyCountByStatus();
        return ResponseEntity.ok(ApiResponse.success(report, "Thống kê số lượng hợp đồng theo trạng thái thành công"));
    }

    /**
     * GET /reports/total-premium-by-status
     * FR-REP-02: Reports the sum of totalPremium for each policy status
     */
    @GetMapping("/total-premium-by-status")
    public ResponseEntity<ApiResponse<List<StatusPremiumReportDto>>> getTotalPremiumByStatus() {
        List<StatusPremiumReportDto> report = reportService.getTotalPremiumByStatus();
        return ResponseEntity.ok(ApiResponse.success(report, "Thống kê tổng doanh thu phí theo trạng thái thành công"));
    }

    /**
     * GET /reports/top-premium-policies
     * FR-REP-03: Reports the top five insurance policies with the highest premium amounts
     */
    @GetMapping("/top-premium-policies")
    public ResponseEntity<ApiResponse<List<TopPolicyReportDto>>> getTopPremiumPolicies() {
        List<TopPolicyReportDto> report = reportService.getTop5HighestPremiumPolicies();
        return ResponseEntity.ok(ApiResponse.success(report, "Top 5 hợp đồng có phí bảo hiểm cao nhất"));
    }

    /**
     * GET /reports/premium-by-effective-month
     * FR-REP-04: Reports total premiums by effective month using $month operator on effectiveDate
     */
    @GetMapping("/premium-by-effective-month")
    public ResponseEntity<ApiResponse<List<MonthlyPremiumReportDto>>> getPremiumByEffectiveMonth() {
        List<MonthlyPremiumReportDto> report = reportService.getPremiumByEffectiveMonth();
        return ResponseEntity.ok(ApiResponse.success(report, "Thống kê doanh thu phí theo tháng hiệu lực"));
    }
}
