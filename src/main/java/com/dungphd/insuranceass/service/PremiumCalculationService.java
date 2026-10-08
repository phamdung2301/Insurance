package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Coverage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * PremiumCalculationService - Nghiệp vụ tính phí bảo hiểm doanh nghiệp
 *
 * Công thức tính phí:
 *   Premium = (Limit × BaseRate%) × TermFactor × TypeFactor × DeductibleAdjustment
 *
 * Trong đó:
 *   - BaseRate%: Tỷ lệ phí gốc theo loại coverage (từ bảng định mức)
 *   - TermFactor: Hệ số theo thời hạn (12T=1.0, 24T=1.85, 36T=2.65)
 *   - TypeFactor: Hệ số theo loại gói (STANDARD=1.0, ENHANCED=1.25, COMPREHENSIVE=1.5)
 *   - DeductibleAdjustment: Chiết khấu theo mức miễn trừ
 */
@Slf4j
@Service
public class PremiumCalculationService {

    /**
     * Bảng tỷ lệ phí cơ bản (BaseRate %) theo loại coverage
     * Nguồn: Insurance Pricing Matrix - Industry Standard
     */
    public static final Map<String, Double> BASE_RATES = new LinkedHashMap<>();

    static {
        BASE_RATES.put("PROPERTY",  0.075); // Tài sản & Cháy nổ: 0.075% of limit
        BASE_RATES.put("GL",        0.120); // Trách nhiệm Công cộng: 0.12%
        BASE_RATES.put("EL",        0.160); // Trách nhiệm Người LĐ: 0.16%
        BASE_RATES.put("BI",        0.145); // Gián đoạn Kinh doanh: 0.145%
        BASE_RATES.put("MARINE",    0.055); // Hàng hải & Vận chuyển: 0.055%
        BASE_RATES.put("CYBER",     0.200); // An ninh mạng: 0.20%
        BASE_RATES.put("DO",        0.180); // Directors & Officers: 0.18%
        BASE_RATES.put("WC",        0.130); // Workers Compensation: 0.13%
    }

    /**
     * Term multiplier - hệ số thời hạn bảo hiểm
     */
    private static final Map<Integer, Double> TERM_FACTORS = Map.of(
        12, 1.00,  // 1 year
        24, 1.85,  // 2 years (discount 7.5%)
        36, 2.65   // 3 years (discount 11.7%)
    );

    /**
     * Type multiplier - hệ số loại gói
     */
    private static final Map<String, Double> TYPE_FACTORS = Map.of(
        "STANDARD",      1.00,
        "ENHANCED",      1.25,
        "COMPREHENSIVE", 1.50
    );

    /**
     * Tính phí bảo hiểm cho một coverage
     * @param coverage Coverage entity với limit, deductible, termMonths, coverageType, baseRate
     * @return Premium (USD)
     */
    public double calculate(Coverage coverage) {
        if (coverage == null || coverage.getLimit() == null || coverage.getLimit() <= 0) {
            return 0.0;
        }

        double limit = coverage.getLimit();

        // 1. Get base rate
        double baseRate;
        if (coverage.getBaseRate() != null && coverage.getBaseRate() > 0) {
            baseRate = coverage.getBaseRate(); // use provided rate
        } else {
            baseRate = BASE_RATES.getOrDefault(
                coverage.getCoverageCode() != null ? coverage.getCoverageCode().toUpperCase() : "",
                0.100 // default 0.1% if unknown coverage type
            );
        }

        // 2. Term factor
        int termMonths = (coverage.getTermMonths() != null && coverage.getTermMonths() > 0)
            ? coverage.getTermMonths() : 12;
        double termFactor = TERM_FACTORS.getOrDefault(termMonths, termMonths / 12.0);

        // 3. Type factor
        String covType = (coverage.getCoverageType() != null) ? coverage.getCoverageType().toUpperCase() : "STANDARD";
        double typeFactor = TYPE_FACTORS.getOrDefault(covType, 1.0);

        // 4. Deductible adjustment (higher deductible → lower premium)
        double deductibleAdj = 1.0;
        if (coverage.getDeductible() != null && coverage.getDeductible() > 0 && limit > 0) {
            double deductibleRatio = coverage.getDeductible() / limit;
            if (deductibleRatio >= 0.01) deductibleAdj = 0.92; // 8% discount
            if (deductibleRatio >= 0.02) deductibleAdj = 0.85; // 15% discount
            if (deductibleRatio >= 0.05) deductibleAdj = 0.78; // 22% discount
            if (deductibleRatio >= 0.10) deductibleAdj = 0.70; // 30% discount
        }

        // 5. Compute premium
        double premium = (limit * baseRate / 100.0) * termFactor * typeFactor * deductibleAdj;

        // Round to 2 decimal places
        return Math.round(premium * 100.0) / 100.0;
    }

    /**
     * Tính phí cho một coverage từ DTO parameters trực tiếp
     */
    public double calculate(String coverageCode, String coverageType, Double limit,
                            Double deductible, Integer termMonths, Double overrideBaseRate) {
        Coverage cov = Coverage.builder()
            .coverageCode(coverageCode)
            .coverageType(coverageType)
            .limit(limit)
            .deductible(deductible)
            .termMonths(termMonths)
            .baseRate(overrideBaseRate)
            .build();
        return calculate(cov);
    }

    /**
     * Xuất file Excel template với bảng định mức phí bảo hiểm
     * Dùng để admin download, điều chỉnh base rate, rồi upload lại
     */
    public byte[] exportPremiumRateTemplate(List<Coverage> sampleCoverages) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Sheet 1: Bảng tỷ lệ phí cơ bản
            Sheet rateSheet = workbook.createSheet("Bảng Định Mức Phí");
            createRateTableSheet(workbook, rateSheet);

            // Sheet 2: Bảng tính phí mẫu
            Sheet calcSheet = workbook.createSheet("Tính Toán Premium");
            createPremiumCalculationSheet(workbook, calcSheet, sampleCoverages);

            // Sheet 3: Hướng dẫn
            Sheet guideSheet = workbook.createSheet("Hướng Dẫn");
            createGuideSheet(workbook, guideSheet);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Import base rates từ Excel file (Sheet 1: Bảng Định Mức Phí)
     * Cho phép admin cập nhật bảng tỷ lệ phí từ file Excel
     */
    public Map<String, Double> importBaseRatesFromExcel(MultipartFile file) throws IOException {
        Map<String, Double> importedRates = new LinkedHashMap<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("File Excel không có dữ liệu");

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell codeCell = row.getCell(0);
                Cell rateCell = row.getCell(2); // Column C: Base Rate %

                if (codeCell == null || rateCell == null) continue;

                String code = getCellStringValue(codeCell).trim().toUpperCase();
                if (code.isEmpty()) continue;

                double rate = 0.0;
                if (rateCell.getCellType() == CellType.NUMERIC) {
                    rate = rateCell.getNumericCellValue();
                } else if (rateCell.getCellType() == CellType.STRING) {
                    try {
                        String rawStr = rateCell.getStringCellValue().replace("%", "").trim();
                        rate = Double.parseDouble(rawStr);
                    } catch (NumberFormatException ignored) {}
                }

                // If rate looks like percentage > 1 (e.g. 7.5% represented as 7.5 instead of 0.075)
                if (rate > 1.0) {
                    rate = rate / 100.0;
                }

                if (rate > 0) {
                    importedRates.put(code, rate);
                    BASE_RATES.put(code, rate); // update in-memory rates
                    log.info("Imported base rate: {} = {}%", code, rate * 100);
                }
            }
        }
        return importedRates;
    }

    /**
     * Xuất file Excel tính toán phí cho một bộ coverages cụ thể
     */
    public byte[] exportPolicyPremiumCalculation(String policyNumber, String insuredName,
                                                  List<Coverage> coverages) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Premium Calculation - " + policyNumber);

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG TỔNG HỢP PHÍ BẢO HIỂM DOANH NGHIỆP");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 8));

            rowNum++;
            Row infoRow1 = sheet.createRow(rowNum++);
            createCell(infoRow1, 0, "Số Hợp Đồng:", headerStyle);
            createCell(infoRow1, 1, policyNumber, dataStyle);
            createCell(infoRow1, 4, "Bên Mua BH:", headerStyle);
            createCell(infoRow1, 5, insuredName, dataStyle);

            rowNum++;

            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"STT", "Mã Gói", "Tên Gói Quyền Lợi", "Loại Gói",
                    "Hạn Mức Bồi Thường (USD)", "Mức Miễn Trừ (USD)", "Tỷ Lệ Phí (%)",
                    "Thời Hạn (tháng)", "Phí Bảo Hiểm (USD)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            double totalPremium = 0;
            int stt = 1;
            for (Coverage cov : coverages) {
                double premium = (cov.getPremium() != null) ? cov.getPremium() : calculate(cov);
                totalPremium += premium;

                Row dataRow = sheet.createRow(rowNum++);
                createCell(dataRow, 0, String.valueOf(stt++), dataStyle);
                createCell(dataRow, 1, cov.getCoverageCode() != null ? cov.getCoverageCode() : "", dataStyle);
                createCell(dataRow, 2, cov.getCoverageName() != null ? cov.getCoverageName() : "", dataStyle);
                createCell(dataRow, 3, cov.getCoverageType() != null ? cov.getCoverageType() : "STANDARD", dataStyle);
                createMoneyCell(dataRow, 4, cov.getLimit() != null ? cov.getLimit() : 0.0, moneyStyle);
                createMoneyCell(dataRow, 5, cov.getDeductible() != null ? cov.getDeductible() : 0.0, moneyStyle);

                double rate = cov.getBaseRate() != null ? cov.getBaseRate()
                        : BASE_RATES.getOrDefault(cov.getCoverageCode() != null ? cov.getCoverageCode().toUpperCase() : "", 0.1);
                Cell rateCell = dataRow.createCell(6);
                rateCell.setCellValue(rate * 100); // show as 0.075 → 0.075%
                rateCell.setCellStyle(dataStyle);

                createCell(dataRow, 7, String.valueOf(cov.getTermMonths() != null ? cov.getTermMonths() : 12), dataStyle);
                createMoneyCell(dataRow, 8, premium, moneyStyle);
            }

            // Total row
            rowNum++;
            Row totalRow = sheet.createRow(rowNum);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum, rowNum, 0, 7));
            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("TỔNG PHÍ BẢO HIỂM");
            totalLabelCell.setCellStyle(totalStyle);
            Cell totalValueCell = totalRow.createCell(8);
            totalValueCell.setCellValue(totalPremium);
            totalValueCell.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ========================
    // Private helper methods
    // ========================

    private void createRateTableSheet(Workbook workbook, Sheet sheet) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Mã Coverage", "Tên Loại Bảo Hiểm", "Tỷ Lệ Phí Gốc (%)", "Mô Tả"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        String[][] rateData = {
            {"PROPERTY", "Bảo Hiểm Tài Sản & Cháy Nổ (Property All Risks)", "0.075", "% trên hạn mức"},
            {"GL",       "Bảo Hiểm Trách Nhiệm Công Cộng (General Liability)", "0.120", "% trên hạn mức"},
            {"EL",       "Bảo Hiểm TNSDLĐ (Employer Liability)", "0.160", "% trên hạn mức"},
            {"BI",       "Bảo Hiểm Gián Đoạn Kinh Doanh (Business Interruption)", "0.145", "% trên hạn mức"},
            {"MARINE",   "Bảo Hiểm Hàng Hải & Vận Chuyển (Marine Cargo)", "0.055", "% trên hạn mức"},
            {"CYBER",    "Bảo Hiểm An Ninh Mạng (Cyber Liability)", "0.200", "% trên hạn mức"},
            {"DO",       "Bảo Hiểm D&O (Directors & Officers)", "0.180", "% trên hạn mức"},
            {"WC",       "Bảo Hiểm Tai Nạn LĐ (Workers Compensation)", "0.130", "% trên hạn mức"},
        };

        int rowNum = 1;
        for (String[] row : rateData) {
            Row dataRow = sheet.createRow(rowNum++);
            for (int i = 0; i < row.length; i++) {
                Cell cell = dataRow.createCell(i);
                cell.setCellValue(row[i]);
                cell.setCellStyle(dataStyle);
            }
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void createPremiumCalculationSheet(Workbook workbook, Sheet sheet, List<Coverage> samples) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Mã Coverage", "Loại Gói", "Hạn Mức (USD)", "Miễn Trừ (USD)",
                "Tỷ Lệ Gốc (%)", "Thời Hạn (tháng)", "Phí Tính Toán (USD)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Sample rows using base rates
        int rowNum = 1;
        for (Map.Entry<String, Double> entry : BASE_RATES.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue("STANDARD");
            row.createCell(2).setCellValue(1000000);
            row.createCell(3).setCellValue(5000);
            row.createCell(4).setCellValue(entry.getValue() * 100);
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(calculate(entry.getKey(), "STANDARD", 1000000.0, 5000.0, 12, null));
            for (int i = 0; i < 7; i++) row.getCell(i).setCellStyle(dataStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void createGuideSheet(Workbook workbook, Sheet sheet) {
        String[] guide = {
            "HƯỚNG DẪN SỬ DỤNG FILE EXCEL TÍNH PHÍ BẢO HIỂM",
            "",
            "1. Sheet 'Bảng Định Mức Phí': Chứa bảng tỷ lệ phí gốc (Base Rate) của từng loại coverage.",
            "   → Admin có thể điều chỉnh cột 'Tỷ Lệ Phí Gốc (%)' rồi upload lại qua API.",
            "",
            "2. Sheet 'Tính Toán Premium': Bảng tính phí mẫu cho từng loại coverage.",
            "   → Dùng để kiểm tra kết quả tính toán.",
            "",
            "CÔNG THỨC TÍNH PHÍ:",
            "   Premium = (Limit × BaseRate%) × TermFactor × TypeFactor × DeductibleAdjustment",
            "",
            "Trong đó:",
            "   - Limit: Hạn mức bồi thường (USD)",
            "   - BaseRate%: Tỷ lệ phí gốc (xem bảng định mức)",
            "   - TermFactor: 12 tháng = 1.0 | 24 tháng = 1.85 | 36 tháng = 2.65",
            "   - TypeFactor: STANDARD = 1.0 | ENHANCED = 1.25 | COMPREHENSIVE = 1.50",
            "   - DeductibleAdjustment: Chiết khấu theo mức miễn trừ (cao hơn → giảm phí nhiều hơn)",
            "",
            "VÍ DỤ:",
            "   Coverage PROPERTY, Limit = $2,000,000, BaseRate = 0.075%",
            "   Term = 12 tháng, Type = STANDARD, Deductible = $5,000",
            "   → Premium = (2,000,000 × 0.075/100) × 1.0 × 1.0 × 1.0 = $1,500"
        };

        for (int i = 0; i < guide.length; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(guide[i]);
        }
        sheet.autoSizeColumn(0);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createMoneyCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font whiteFont = wb.createFont();
        whiteFont.setBold(true);
        whiteFont.setColor(IndexedColors.WHITE.getIndex());
        whiteFont.setFontHeightInPoints((short) 11);
        style.setFont(whiteFont);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(false);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook wb) {
        CellStyle style = createDataStyle(wb);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createTotalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.DOUBLE);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
}
