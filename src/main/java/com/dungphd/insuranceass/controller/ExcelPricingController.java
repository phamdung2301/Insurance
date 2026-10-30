package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.CalculatePremiumRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.PremiumCalculationResultDto;
import com.dungphd.insuranceass.dto.response.RateMatrixItemDto;
import com.dungphd.insuranceass.model.RateImport;
import com.dungphd.insuranceass.service.PricingExcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/excel", "/api/excel"})
@RequiredArgsConstructor
public class ExcelPricingController {

    private final PricingExcelService pricingExcelService;

    /**
     * GET /excel/template
     * FR-CALC-02: Download sample Excel pricing matrix template with formulas and base rates
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadPricingTemplate() {
        byte[] excelBytes = pricingExcelService.generatePricingTemplate();
        String filename = "InsurancePricingMatrix_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * POST /excel/import-rates
     * FR-CALC-03: Upload an Excel file to update in-memory base rate catalog
     */
    @PostMapping(value = "/import-rates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importRates(
            @RequestParam("file") MultipartFile file
    ) {
        Map<String, Object> result = pricingExcelService.importRatesFromExcel(file);
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật biểu phí định phí bảo hiểm thành công"));
    }

    /**
     * POST /excel/calculate
     * FR-CALC-05: Real-time indicative premium calculation without saving to DB
     */
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<PremiumCalculationResultDto>> calculatePremium(
            @Valid @RequestBody CalculatePremiumRequest request
    ) {
        PremiumCalculationResultDto result = pricingExcelService.calculateIndicativePremium(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Tính toán phí bảo hiểm thành công"));
    }

    /**
     * GET /excel/policy/{policyNumber}/download
     * FR-CALC-04: Export individual policy premium calculation breakdown to Excel
     */
    @GetMapping("/policy/{policyNumber}/download")
    public ResponseEntity<byte[]> downloadPolicyBreakdown(@PathVariable("policyNumber") String policyNumber) {
        byte[] excelBytes = pricingExcelService.exportPolicyPremiumBreakdown(policyNumber);
        String filename = "PremiumCalc_" + policyNumber + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * GET /excel/imports (đợt 3C, việc 2): lịch sử import biểu phí, mới
     * nhất trước. Nằm trong matcher /excel/** (ADMIN + UNDERWRITER) sẵn
     * có, không sửa SecurityConfig cho việc này.
     */
    @GetMapping("/imports")
    public ResponseEntity<ApiResponse<List<RateImport>>> listImports() {
        return ResponseEntity.ok(ApiResponse.success(
                pricingExcelService.listRecentImports(), "Lịch sử import biểu phí."));
    }

    /**
     * GET /excel/rates
     * Retrieve list of active pricing rates
     */
    @GetMapping("/rates")
    public ResponseEntity<ApiResponse<List<RateMatrixItemDto>>> getActiveRates() {
        List<RateMatrixItemDto> rates = pricingExcelService.getCurrentRateCatalog();
        return ResponseEntity.ok(ApiResponse.success(rates, "Danh mục biểu phí bảo hiểm hiện hành"));
    }
}
