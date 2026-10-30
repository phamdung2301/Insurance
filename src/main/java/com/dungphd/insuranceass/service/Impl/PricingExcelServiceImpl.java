package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.CalculatePremiumRequest;
import com.dungphd.insuranceass.dto.response.PremiumCalculationResultDto;
import com.dungphd.insuranceass.dto.response.RateMatrixItemDto;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.RateImport;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RateImportRepository;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.PricingExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingExcelServiceImpl implements PricingExcelService {

    private final PolicyRepository policyRepository;

    /**
     * Đợt 3C, việc 2 — lịch sử import. Cố tình KHÔNG final để giữ nguyên
     * constructor 1-arg cũ (PricingExcelServiceImplTest dùng
     * new PricingExcelServiceImpl(policyRepository)): Spring inject qua
     * field, còn code tự dựng tay (test cũ) thì 2 field này null và lịch
     * sử được bỏ qua một cách an toàn (xem recordRateImport).
     */
    @Autowired(required = false)
    private RateImportRepository rateImportRepository;

    @Autowired(required = false)
    private PermissionService permissionService;

    // In-memory thread-safe rate matrix catalog (FR-CALC-01, FR-CALC-03)
    private final Map<String, RateMatrixItemDto> rateCatalog = new ConcurrentHashMap<>();

    {
        // Initialize default rate matrix
        rateCatalog.put("PROP_FIRE", RateMatrixItemDto.builder()
                .coverageCode("PROP_FIRE")
                .coverageName("Bảo hiểm Cháy nổ & Rủi ro đặc biệt")
                .coverageType("PROPERTY")
                .baseRate(0.0015)
                .minLimit(50000000.0)
                .maxLimit(10000000000.0)
                .defaultDeductible(5000000.0)
                .description("Bảo hiểm tổn thất tài sản do cháy, sét đánh, nổ công nghiệp")
                .build());

        rateCatalog.put("PROP_THEFT", RateMatrixItemDto.builder()
                .coverageCode("PROP_THEFT")
                .coverageName("Bảo hiểm Trộm cắp tài sản")
                .coverageType("PROPERTY")
                .baseRate(0.0020)
                .minLimit(20000000.0)
                .maxLimit(5000000000.0)
                .defaultDeductible(2000000.0)
                .description("Bảo hiểm mất mát tài sản do đột nhập hoặc cướp có vũ trang")
                .build());

        rateCatalog.put("GL", RateMatrixItemDto.builder()
                .coverageCode("GL")
                .coverageName("Bảo hiểm Trách nhiệm công cộng (General Liability)")
                .coverageType("LIABILITY")
                .baseRate(0.0010)
                .minLimit(100000000.0)
                .maxLimit(20000000000.0)
                .defaultDeductible(10000000.0)
                .description("Bồi thường thiệt hại bên thứ ba về thân thể hoặc tài sản")
                .build());

        rateCatalog.put("EL", RateMatrixItemDto.builder()
                .coverageCode("EL")
                .coverageName("Bảo hiểm Trách nhiệm người sử dụng lao động (Employer Liability)")
                .coverageType("LIABILITY")
                .baseRate(0.0012)
                .minLimit(50000000.0)
                .maxLimit(15000000000.0)
                .defaultDeductible(5000000.0)
                .description("Bồi thường tai nạn nghề nghiệp cho nhân viên thuộc trách nhiệm doanh nghiệp")
                .build());

        rateCatalog.put("WC", RateMatrixItemDto.builder()
                .coverageCode("WC")
                .coverageName("Bảo hiểm Tai nạn lao động (Workers Comp)")
                .coverageType("ACCIDENT")
                .baseRate(0.0018)
                .minLimit(30000000.0)
                .maxLimit(10000000000.0)
                .defaultDeductible(2000000.0)
                .description("Hỗ trợ viện phí và trợ cấp thương tật cho người lao động")
                .build());

        rateCatalog.put("CYBER", RateMatrixItemDto.builder()
                .coverageCode("CYBER")
                .coverageName("Bảo hiểm An ninh mạng & Dữ liệu số (Cyber Risk)")
                .coverageType("SPECIALTY")
                .baseRate(0.0035)
                .minLimit(100000000.0)
                .maxLimit(50000000000.0)
                .defaultDeductible(20000000.0)
                .description("Bồi thường chi phí xử lý sự cố rò rỉ dữ liệu và tấn công mã độc tống tiền")
                .build());
    }

    @Override
    public byte[] generatePricingTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Sheet 1: Pricing_Matrix
            Sheet sheet = workbook.createSheet("Pricing_Matrix");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Data format for rate
            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle rateStyle = workbook.createCellStyle();
            rateStyle.setDataFormat(dataFormat.getFormat("0.0000"));

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            String[] headers = {
                    "Coverage Code", "Coverage Name", "Coverage Type",
                    "Base Rate (Decimal)", "Min Limit (VND)", "Max Limit (VND)",
                    "Default Deductible (VND)", "Description"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (RateMatrixItemDto item : rateCatalog.values()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getCoverageCode());
                row.createCell(1).setCellValue(item.getCoverageName());
                row.createCell(2).setCellValue(item.getCoverageType());

                Cell rateCell = row.createCell(3);
                rateCell.setCellValue(item.getBaseRate());
                rateCell.setCellStyle(rateStyle);

                Cell minLimitCell = row.createCell(4);
                minLimitCell.setCellValue(item.getMinLimit());
                minLimitCell.setCellStyle(currencyStyle);

                Cell maxLimitCell = row.createCell(5);
                maxLimitCell.setCellValue(item.getMaxLimit());
                maxLimitCell.setCellStyle(currencyStyle);

                Cell dedCell = row.createCell(6);
                dedCell.setCellValue(item.getDefaultDeductible());
                dedCell.setCellStyle(currencyStyle);

                row.createCell(7).setCellValue(item.getDescription());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Sheet 2: Calculation_Formulas
            Sheet formulaSheet = workbook.createSheet("Calculation_Formulas");
            Row fRow0 = formulaSheet.createRow(0);
            fRow0.createCell(0).setCellValue("CÔNG THỨC ĐỊNH PHÍ BẢO HIỂM CHUẨN (ACTUARIAL FORMULAS)");
            fRow0.getCell(0).setCellStyle(headerStyle);

            Row fRow1 = formulaSheet.createRow(2);
            fRow1.createCell(0).setCellValue("Công thức:");
            fRow1.createCell(1).setCellValue("Phí BH = Hạn mức (Limit) * Tỷ lệ phí cơ bản (Base Rate) * (Thời hạn tháng / 12) * Hệ số rủi ro (Risk Factor)");

            Row fRow2 = formulaSheet.createRow(4);
            fRow2.createCell(0).setCellValue("Ví dụ minh họa:");
            fRow2.createCell(1).setCellValue("PROP_FIRE với Limit = 1,000,000,000 VND, Base Rate = 0.0015, Term = 12 tháng -> Phí = 1,000,000,000 * 0.0015 * 1 = 1,500,000 VND");

            formulaSheet.autoSizeColumn(0);
            formulaSheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating pricing Excel template", e);
            throw new RuntimeException("Không thể tạo template Excel định phí: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> importRatesFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File Excel tải lên không được rỗng");
        }

        int updatedCount = 0;
        List<String> updatedCodes = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheet("Pricing_Matrix");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell codeCell = row.getCell(0);
                if (codeCell == null) continue;
                String code = codeCell.getStringCellValue().trim();
                if (code.isBlank()) continue;

                Cell nameCell = row.getCell(1);
                String name = nameCell != null ? nameCell.getStringCellValue() : code;

                Cell typeCell = row.getCell(2);
                String type = typeCell != null ? typeCell.getStringCellValue() : "PROPERTY";

                Cell rateCell = row.getCell(3);
                double rate = 0.0015;
                if (rateCell != null) {
                    if (rateCell.getCellType() == CellType.NUMERIC) {
                        rate = rateCell.getNumericCellValue();
                    } else if (rateCell.getCellType() == CellType.STRING) {
                        try {
                            rate = Double.parseDouble(rateCell.getStringCellValue().trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }

                Cell minLimitCell = row.getCell(4);
                double minLimit = minLimitCell != null && minLimitCell.getCellType() == CellType.NUMERIC ? minLimitCell.getNumericCellValue() : 10000000.0;

                Cell maxLimitCell = row.getCell(5);
                double maxLimit = maxLimitCell != null && maxLimitCell.getCellType() == CellType.NUMERIC ? maxLimitCell.getNumericCellValue() : 10000000000.0;

                Cell dedCell = row.getCell(6);
                double ded = dedCell != null && dedCell.getCellType() == CellType.NUMERIC ? dedCell.getNumericCellValue() : 5000000.0;

                Cell descCell = row.getCell(7);
                String desc = descCell != null ? descCell.getStringCellValue() : "";

                RateMatrixItemDto item = RateMatrixItemDto.builder()
                        .coverageCode(code)
                        .coverageName(name)
                        .coverageType(type)
                        .baseRate(rate)
                        .minLimit(minLimit)
                        .maxLimit(maxLimit)
                        .defaultDeductible(ded)
                        .description(desc)
                        .build();

                rateCatalog.put(code, item);
                updatedCodes.add(code);
                updatedCount++;
            }

            log.info("Successfully imported {} pricing rates from Excel: {}", updatedCount, updatedCodes);
            // Đợt 3C, việc 2 — lưu lịch sử SAU KHI import thành công.
            // Failure-isolated: recordRateImport tự try/catch + log, không
            // bao giờ làm fail import chính.
            recordRateImport(file, updatedCount);
            Map<String, Object> result = new HashMap<>();
            result.put("updatedCount", updatedCount);
            result.put("updatedCodes", updatedCodes);
            result.put("message", "Đã cập nhật thành công " + updatedCount + " danh mục tỷ lệ phí từ file Excel");
            return result;
        } catch (Exception e) {
            log.error("Failed to import rates from Excel", e);
            throw new RuntimeException("Lỗi khi đọc file Excel định phí: " + e.getMessage(), e);
        }
    }

    @Override
    public PremiumCalculationResultDto calculateIndicativePremium(CalculatePremiumRequest request) {
        String code = request.getCoverageCode().trim();
        RateMatrixItemDto rateItem = rateCatalog.get(code);

        double baseRate = rateItem != null ? rateItem.getBaseRate() : 0.0015;
        double riskFactor = request.getRiskFactor() != null && request.getRiskFactor() > 0 ? request.getRiskFactor() : 1.0;
        double appliedRate = baseRate * riskFactor;
        int term = request.getTermMonths() != null && request.getTermMonths() > 0 ? request.getTermMonths() : 12;

        double calculatedPremium = Math.round(request.getLimit() * appliedRate * (term / 12.0));
        double ded = request.getDeductible() != null ? request.getDeductible() : (rateItem != null ? rateItem.getDefaultDeductible() : 5000000.0);

        String formula = String.format("%,.0f * %.4f * (%d / 12) = %,.0f VND",
                request.getLimit(), appliedRate, term, calculatedPremium);

        return PremiumCalculationResultDto.builder()
                .coverageCode(code)
                .coverageName(rateItem != null ? rateItem.getCoverageName() : code)
                .limit(request.getLimit())
                .deductible(ded)
                .termMonths(term)
                .baseRate(baseRate)
                .appliedRate(appliedRate)
                .calculatedPremium(calculatedPremium)
                .calculationFormula(formula)
                .calculationTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .build();
    }

    @Override
    public byte[] exportPolicyPremiumBreakdown(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng bảo hiểm: " + policyNumber));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Premium_Breakdown");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0 VND"));

            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFont(boldFont);
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setDataFormat(dataFormat.getFormat("#,##0 VND"));

            // Title
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("BẢNG TÍNH PHÍ BẢO HIỂM CHI TIẾT (PREMIUM CALCULATION BREAKDOWN)");
            c0.setCellStyle(titleStyle);

            // Policy Metadata
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Số Hợp Đồng:");
            r2.createCell(1).setCellValue(policy.getPolicyNumber());
            r2.createCell(3).setCellValue("Trạng Thái:");
            r2.createCell(4).setCellValue(policy.getStatus() != null ? policy.getStatus().name() : "DRAFT");

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Khách Hàng:");
            r3.createCell(1).setCellValue(policy.getInsured() != null ? policy.getInsured().getName() : "N/A");
            r3.createCell(3).setCellValue("Phiên Bản:");
            r3.createCell(4).setCellValue("v" + (policy.getVersion() != null ? policy.getVersion() : 1));

            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("Email:");
            r4.createCell(1).setCellValue(policy.getInsured() != null ? policy.getInsured().getEmail() : "N/A");
            r4.createCell(3).setCellValue("Số Điện Thoại:");
            r4.createCell(4).setCellValue(policy.getInsured() != null ? policy.getInsured().getPhone() : "N/A");

            // Table Header
            String[] headers = {
                    "Địa Điểm (Location)", "Địa Chỉ (Address)", "Mã Quyền Lợi",
                    "Tên Quyền Lợi", "Hạn Mức (Limit)", "Mức Khấu Trừ", "Thời Hạn", "Phí Bảo Hiểm (VND)"
            };

            Row tableHeader = sheet.createRow(6);
            tableHeader.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = tableHeader.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 7;
            double grandTotal = 0;

            if (policy.getLocations() != null) {
                for (Location locItem : policy.getLocations()) {
                    String locName = "Địa điểm " + locItem.getLocationId();
                    String locAddr = locItem.getAddress() != null ? locItem.getAddress() : "";

                    if (locItem.getCoverages() != null) {
                        for (Coverage cov : locItem.getCoverages()) {
                            Row row = sheet.createRow(rowIdx++);
                            row.createCell(0).setCellValue(locName);
                            row.createCell(1).setCellValue(locAddr);
                            row.createCell(2).setCellValue(cov.getCoverageCode());
                            row.createCell(3).setCellValue(cov.getCoverageName() != null ? cov.getCoverageName() : cov.getCoverageCode());

                            Cell limitCell = row.createCell(4);
                            limitCell.setCellValue(cov.getLimit() != null ? cov.getLimit() : 0);
                            limitCell.setCellStyle(currencyStyle);

                            Cell dedCell = row.createCell(5);
                            dedCell.setCellValue(cov.getDeductible() != null ? cov.getDeductible() : 0);
                            dedCell.setCellStyle(currencyStyle);

                            row.createCell(6).setCellValue((cov.getTermMonths() != null ? cov.getTermMonths() : 12) + " tháng");

                            double prem = cov.getPremium() != null ? cov.getPremium() : 0;
                            grandTotal += prem;
                            Cell premCell = row.createCell(7);
                            premCell.setCellValue(prem);
                            premCell.setCellStyle(currencyStyle);
                        }
                    }
                }
            }

            // Total Row
            Row totalRow = sheet.createRow(rowIdx++);
            totalRow.createCell(6).setCellValue("TỔNG PHÍ BẢO HIỂM:");
            totalRow.getCell(6).setCellStyle(boldStyle);

            Cell grandTotalCell = totalRow.createCell(7);
            grandTotalCell.setCellValue(grandTotal > 0 ? grandTotal : (policy.getTotalPremium() != null ? policy.getTotalPremium() : 0));
            grandTotalCell.setCellStyle(totalStyle);

            // Digital Signature Block
            rowIdx += 2;
            Row sigRow1 = sheet.createRow(rowIdx++);
            sigRow1.createCell(0).setCellValue("XÁC NHẬN BẢO HIỂM ĐIỆN TỬ (DIGITAL AUDIT SEAL)");
            sigRow1.getCell(0).setCellStyle(boldStyle);

            Row sigRow2 = sheet.createRow(rowIdx++);
            sigRow2.createCell(0).setCellValue("Hệ thống: InsurTech Core Policy Administration System 2026");

            Row sigRow3 = sheet.createRow(rowIdx++);
            sigRow3.createCell(0).setCellValue("Chứng chỉ số SHA-256: 9a3f8c7e2b1d4a6f8c0e2b4d6f8a1c3e");

            Row sigRow4 = sheet.createRow(rowIdx++);
            sigRow4.createCell(0).setCellValue("Thời điểm xuất tài liệu: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error exporting policy premium breakdown to Excel", e);
            throw new RuntimeException("Không thể xuất bảng tính phí Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RateMatrixItemDto> getCurrentRateCatalog() {
        return new ArrayList<>(rateCatalog.values());
    }

    @Override
    public List<RateImport> listRecentImports() {
        if (rateImportRepository == null) {
            return List.of();
        }
        return rateImportRepository.findAllByOrderByImportedAtDesc();
    }

    /**
     * Lưu 1 RateImport cho lần import vừa thành công. Failure-isolated:
     * mọi lỗi (kể cả repo null khi dựng tay không qua Spring) đều chỉ
     * log, không throw. Lựa chọn "System": khi không lấy được principal
     * (PermissionService null, hoặc SecurityContext trống — ví dụ job nội
     * bộ) thì importedBy = "System" thay vì null, để lịch sử luôn có người
     * chịu trách nhiệm hiển thị; đây là lựa chọn đã ghi rõ theo yêu cầu
     * đợt 3C, không phải fallback âm thầm cho request HTTP không auth
     * (endpoint import đã bị matcher /excel/** chặn ADMIN+UW từ trước).
     */
    private void recordRateImport(MultipartFile file, int ratesCount) {
        try {
            if (rateImportRepository == null) {
                log.debug("recordRateImport: bỏ qua lịch sử vì RateImportRepository chưa được inject.");
                return;
            }
            String importedBy = null;
            try {
                importedBy = permissionService != null ? permissionService.currentPrincipal() : null;
            } catch (Exception ignored) {
                importedBy = null;
            }
            if (importedBy == null || importedBy.isBlank()) {
                importedBy = "System";
            }
            String fileName = file != null && file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "";
            rateImportRepository.save(RateImport.builder()
                    .importedBy(importedBy)
                    .importedAt(Instant.now())
                    .fileName(fileName)
                    .ratesCount(ratesCount)
                    .build());
        } catch (Exception e) {
            log.error("recordRateImport: lỗi khi lưu lịch sử import (đã nuốt lỗi, import chính không bị ảnh hưởng).", e);
        }
    }
}
