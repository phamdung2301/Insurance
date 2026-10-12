package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.DocumentGenerationService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerationServiceImpl implements DocumentGenerationService {

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;

    private static final Color PRIMARY_COLOR = new Color(30, 58, 138);       // Navy Blue #1E3A8A
    private static final Color PRIMARY_DARK = new Color(15, 23, 42);         // Dark Slate #0F172A
    private static final Color PRIMARY_LIGHT = new Color(239, 246, 255);     // Light Blue Tint #EFF6FF
    private static final Color ACCENT_GREEN = new Color(5, 150, 105);        // Emerald Green #059669
    private static final Color TEXT_DARK = new Color(30, 41, 59);            // Slate Dark #1E293B
    private static final Color TEXT_MUTED = new Color(100, 116, 139);        // Slate Muted #64748B
    private static final Color BORDER_COLOR = new Color(226, 232, 240);      // Border #E2E8F0
    private static final Color ALT_ROW_COLOR = new Color(248, 250, 252);     // Table Alt #F8FAFC

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());

    @Override
    public byte[] generateCertificateOfInsurancePdf(String policyNumber, Integer version) {
        Policy policy = resolvePolicyVersion(policyNumber, version);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header Banner
            addCertificateHeader(document, policy);

            // 2. Policy & Insured Details Grid
            addPolicyAndInsuredSection(document, policy);

            // 3. Locations & Coverages Table
            addCoveragesScheduleTable(document, policy);

            // 4. Financial Premium Summary Box
            addPremiumSummaryBanner(document, policy);

            // 5. Certification & Authorization Block
            addCertificationFooterBlock(document, policy);

            document.close();
            log.info("Successfully generated Certificate of Insurance PDF for policy {} (v{})", policy.getPolicyNumber(), policy.getVersion());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Certificate of Insurance PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF Certificate: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generatePolicySummaryPdf(String policyNumber, Integer version) {
        Policy policy = resolvePolicyVersion(policyNumber, version);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header Banner
            addSummaryHeader(document, policy);

            // 2. Policy Overview & Key Information
            addPolicyAndInsuredSection(document, policy);

            // 3. Detailed Locations and Coverages Schedule
            addDetailedLocationsSchedule(document, policy);

            // 4. Financial Premium Summary Box
            addPremiumSummaryBanner(document, policy);

            // 5. Terms & Signature Block
            addSummaryTermsAndSignature(document, policy);

            document.close();
            log.info("Successfully generated Policy Summary PDF for policy {} (v{})", policy.getPolicyNumber(), policy.getVersion());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Policy Summary PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Policy Summary PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentMetadataDto> getAvailableDocuments(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number or ID: " + policyNumber));

        int currentVer = policy.getVersion() != null ? policy.getVersion() : 1;

        List<DocumentMetadataDto> docs = new ArrayList<>();

        // 1. Certificate of Insurance (COI)
        docs.add(DocumentMetadataDto.builder()
                .documentId("COI-" + policy.getPolicyNumber() + "-v" + currentVer)
                .documentType("CERTIFICATE")
                .title("Certificate of Commercial Insurance (COI)")
                .fileName("Certificate_" + policy.getPolicyNumber() + "_v" + currentVer + ".pdf")
                .policyNumber(policy.getPolicyNumber())
                .version(currentVer)
                .contentType("application/pdf")
                .downloadUrl("/policies/" + policy.getPolicyNumber() + "/documents/certificate")
                .generatedAt(Instant.now())
                .build());

        // 2. Policy Summary & Schedule
        docs.add(DocumentMetadataDto.builder()
                .documentId("SUM-" + policy.getPolicyNumber() + "-v" + currentVer)
                .documentType("SUMMARY")
                .title("Policy Summary & Coverage Schedule")
                .fileName("Policy_Summary_" + policy.getPolicyNumber() + "_v" + currentVer + ".pdf")
                .policyNumber(policy.getPolicyNumber())
                .version(currentVer)
                .contentType("application/pdf")
                .downloadUrl("/policies/" + policy.getPolicyNumber() + "/documents/summary")
                .generatedAt(Instant.now())
                .build());

        return docs;
    }

    private Policy resolvePolicyVersion(String policyNumber, Integer version) {
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new BadRequestException("Policy number must be provided");
        }

        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with number: " + policyNumber));

        if (version != null && !version.equals(policy.getVersion())) {
            // Retrieve historical snapshot
            return policyVersionRepository.findByPolicyNumberAndVersion(policy.getPolicyNumber(), version)
                    .map(PolicyVersion::getPolicySnapshot)
                    .orElseThrow(() -> new ResourceNotFoundException("Policy version " + version + " not found for policy: " + policyNumber));
        }

        return policy;
    }

    // =========================================================================
    // PDF RENDERING HELPER METHODS
    // =========================================================================

    private void addCertificateHeader(Document document, Policy policy) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{65f, 35f});
        headerTable.setSpacingAfter(12f);

        // Left Header: Brand & Document Title
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setBackgroundColor(PRIMARY_COLOR);
        leftCell.setPadding(12f);

        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(147, 197, 253));
        Paragraph brand = new Paragraph("COMMERCIAL INSURANCE UNDERWRITING CORP", brandFont);
        leftCell.addElement(brand);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
        Paragraph title = new Paragraph("CERTIFICATE OF INSURANCE", titleFont);
        title.setSpacingBefore(2f);
        leftCell.addElement(title);

        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(226, 232, 240));
        Paragraph sub = new Paragraph("Official Evidence of Commercial Liability & Property Coverage", subFont);
        leftCell.addElement(sub);

        // Right Header: Policy Number & Version Badge
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setBackgroundColor(PRIMARY_DARK);
        rightCell.setPadding(12f);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font polLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));
        Paragraph polLabel = new Paragraph("POLICY NUMBER / VERSION", polLabelFont);
        polLabel.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(polLabel);

        Font polNumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Paragraph polNum = new Paragraph(policy.getPolicyNumber() + " (v" + (policy.getVersion() != null ? policy.getVersion() : 1) + ")", polNumFont);
        polNum.setAlignment(Element.ALIGN_RIGHT);
        polNum.setSpacingBefore(2f);
        rightCell.addElement(polNum);

        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ACCENT_GREEN);
        Paragraph status = new Paragraph("STATUS: " + (policy.getStatus() != null ? policy.getStatus().name() : "ACTIVE"), statusFont);
        status.setAlignment(Element.ALIGN_RIGHT);
        status.setSpacingBefore(2f);
        rightCell.addElement(status);

        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addSummaryHeader(Document document, Policy policy) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{65f, 35f});
        headerTable.setSpacingAfter(12f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setBackgroundColor(PRIMARY_COLOR);
        leftCell.setPadding(12f);

        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(147, 197, 253));
        leftCell.addElement(new Paragraph("INSURANCE PORTFOLIO MANAGEMENT", brandFont));

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
        Paragraph title = new Paragraph("POLICY SCHEDULE & SUMMARY", titleFont);
        title.setSpacingBefore(2f);
        leftCell.addElement(title);

        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(226, 232, 240));
        leftCell.addElement(new Paragraph("Comprehensive Terms, Insured Locations & Limits Schedule", subFont));

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setBackgroundColor(PRIMARY_DARK);
        rightCell.setPadding(12f);

        Font polLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));
        Paragraph polLabel = new Paragraph("POLICY SPECIFICATION", polLabelFont);
        polLabel.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(polLabel);

        Font polNumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Paragraph polNum = new Paragraph(policy.getPolicyNumber() + " (v" + (policy.getVersion() != null ? policy.getVersion() : 1) + ")", polNumFont);
        polNum.setAlignment(Element.ALIGN_RIGHT);
        polNum.setSpacingBefore(2f);
        rightCell.addElement(polNum);

        Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.WHITE);
        Paragraph date = new Paragraph("Generated: " + DATE_FORMATTER.format(Instant.now()), dateFont);
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setSpacingBefore(2f);
        rightCell.addElement(date);

        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addPolicyAndInsuredSection(Document document, Policy policy) throws DocumentException {
        PdfPTable infoGrid = new PdfPTable(2);
        infoGrid.setWidthPercentage(100);
        infoGrid.setWidths(new float[]{50f, 50f});
        infoGrid.setSpacingAfter(14f);

        // Insured Info Box
        PdfPCell insuredCell = new PdfPCell();
        insuredCell.setBorderColor(BORDER_COLOR);
        insuredCell.setBorderWidth(1f);
        insuredCell.setBackgroundColor(ALT_ROW_COLOR);
        insuredCell.setPadding(10f);

        Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PRIMARY_COLOR);
        insuredCell.addElement(new Paragraph("INSURED / POLICYHOLDER DETAILS", sectionHeaderFont));

        Insured insured = policy.getInsured();
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_MUTED);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_DARK);

        if (insured != null) {
            insuredCell.addElement(new Paragraph("Name: " + safeStr(insured.getName()), valFont));
            insuredCell.addElement(new Paragraph("Insured ID: " + safeStr(insured.getInsuredId()) + " (" + safeStr(insured.getType()) + ")", valFont));
            insuredCell.addElement(new Paragraph("Address: " + safeStr(insured.getAddress()), valFont));
            insuredCell.addElement(new Paragraph("Email: " + safeStr(insured.getEmail()) + " | Tel: " + safeStr(insured.getPhone()), valFont));
        } else {
            insuredCell.addElement(new Paragraph("Insured information not specified.", valFont));
        }

        // Policy Period & Term Box
        PdfPCell termCell = new PdfPCell();
        termCell.setBorderColor(BORDER_COLOR);
        termCell.setBorderWidth(1f);
        termCell.setBackgroundColor(ALT_ROW_COLOR);
        termCell.setPadding(10f);

        termCell.addElement(new Paragraph("POLICY TERM & VALIDITY PERIOD", sectionHeaderFont));
        termCell.addElement(new Paragraph("Effective Date: " + formatDate(policy.getEffectiveDate()), valFont));
        termCell.addElement(new Paragraph("Expiration Date: " + formatDate(policy.getExpirationDate()), valFont));
        termCell.addElement(new Paragraph("Bound Date: " + formatDate(policy.getBoundDate()), valFont));
        termCell.addElement(new Paragraph("Payment Due Date: " + formatDate(policy.getPaymentDueDate()), valFont));

        infoGrid.addCell(insuredCell);
        infoGrid.addCell(termCell);
        document.add(infoGrid);
    }

    private void addCoveragesScheduleTable(Document document, Policy policy) throws DocumentException {
        Font secTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARY_COLOR);
        Paragraph secTitle = new Paragraph("SCHEDULE OF COVERED LOCATIONS & COVERAGES", secTitleFont);
        secTitle.setSpacingAfter(6f);
        document.add(secTitle);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{18f, 24f, 15f, 15f, 12f, 16f});
        table.setSpacingAfter(12f);

        // Header
        addTableHeaderCell(table, "Location");
        addTableHeaderCell(table, "Coverage Name");
        addTableHeaderCell(table, "Type");
        addTableHeaderCell(table, "Limit (VND)");
        addTableHeaderCell(table, "Deductible");
        addTableHeaderCell(table, "Premium (VND)");

        List<Location> locations = policy.getLocations();
        if (locations == null || locations.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No insured locations or coverages registered.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED)));
            emptyCell.setColspan(6);
            emptyCell.setPadding(10f);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        } else {
            int rowIdx = 0;
            for (Location loc : locations) {
                String locLabel = "Loc #" + loc.getLocationId() + "\n" + truncate(loc.getAddress(), 25);
                if (loc.getCoverages() != null && !loc.getCoverages().isEmpty()) {
                    for (Coverage cov : loc.getCoverages()) {
                        Color rowBg = (rowIdx % 2 == 0) ? Color.WHITE : ALT_ROW_COLOR;

                        addTableCell(table, locLabel, rowBg, Element.ALIGN_LEFT);
                        addTableCell(table, cov.getCoverageName() + " (" + cov.getCoverageCode() + ")", rowBg, Element.ALIGN_LEFT);
                        addTableCell(table, cov.getCoverageType() != null ? cov.getCoverageType() : "STANDARD", rowBg, Element.ALIGN_CENTER);
                        addTableCell(table, formatCurrency(cov.getLimit()), rowBg, Element.ALIGN_RIGHT);
                        addTableCell(table, formatCurrency(cov.getDeductible()), rowBg, Element.ALIGN_RIGHT);
                        addTableCell(table, formatCurrency(cov.getPremium()), rowBg, Element.ALIGN_RIGHT);

                        rowIdx++;
                    }
                } else {
                    Color rowBg = (rowIdx % 2 == 0) ? Color.WHITE : ALT_ROW_COLOR;
                    addTableCell(table, locLabel, rowBg, Element.ALIGN_LEFT);
                    PdfPCell noCov = new PdfPCell(new Phrase("No coverage assigned", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED)));
                    noCov.setColspan(5);
                    noCov.setBackgroundColor(rowBg);
                    noCov.setPadding(5f);
                    table.addCell(noCov);
                    rowIdx++;
                }
            }
        }

        document.add(table);
    }

    private void addDetailedLocationsSchedule(Document document, Policy policy) throws DocumentException {
        Font secTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARY_COLOR);
        Paragraph secTitle = new Paragraph("ITEMIZED LOCATION SCHEDULE & RISK APPRAISAL", secTitleFont);
        secTitle.setSpacingAfter(6f);
        document.add(secTitle);

        List<Location> locations = policy.getLocations();
        if (locations == null || locations.isEmpty()) {
            document.add(new Paragraph("No locations scheduled.", FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED)));
            return;
        }

        for (Location loc : locations) {
            PdfPTable locCard = new PdfPTable(1);
            locCard.setWidthPercentage(100);
            locCard.setSpacingAfter(8f);

            PdfPCell cardCell = new PdfPCell();
            cardCell.setBorderColor(BORDER_COLOR);
            cardCell.setBackgroundColor(ALT_ROW_COLOR);
            cardCell.setPadding(8f);

            Font locHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARY_COLOR);
            cardCell.addElement(new Paragraph("Location #" + loc.getLocationId() + " — " + safeStr(loc.getAddress()), locHeaderFont));

            if (loc.getCoverages() != null && !loc.getCoverages().isEmpty()) {
                double locSubtotal = 0.0;
                for (Coverage cov : loc.getCoverages()) {
                    if (cov.getPremium() != null) locSubtotal += cov.getPremium();
                    Font covFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_DARK);
                    String covText = String.format("• [%s] %s | Limit: %s VND | Deductible: %s VND | Premium: %s VND",
                            cov.getCoverageCode(), cov.getCoverageName(),
                            formatCurrency(cov.getLimit()), formatCurrency(cov.getDeductible()), formatCurrency(cov.getPremium()));
                    cardCell.addElement(new Paragraph(covText, covFont));
                }
                Font subtotalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ACCENT_GREEN);
                Paragraph subtotalP = new Paragraph("Location Subtotal: " + formatCurrency(locSubtotal) + " VND", subtotalFont);
                subtotalP.setAlignment(Element.ALIGN_RIGHT);
                cardCell.addElement(subtotalP);
            }

            locCard.addCell(cardCell);
            document.add(locCard);
        }
    }

    private void addPremiumSummaryBanner(Document document, Policy policy) throws DocumentException {
        PdfPTable banner = new PdfPTable(2);
        banner.setWidthPercentage(100);
        banner.setWidths(new float[]{60f, 40f});
        banner.setSpacingAfter(14f);

        PdfPCell left = new PdfPCell();
        left.setBorderColor(BORDER_COLOR);
        left.setBackgroundColor(PRIMARY_LIGHT);
        left.setPadding(10f);

        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARY_COLOR);
        left.addElement(new Paragraph("TOTAL ANNUAL COMMERCIAL PREMIUM", noteFont));

        Font subNoteFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);
        left.addElement(new Paragraph("Calculated based on risk matrix limits, underwriting factors and location ratings.", subNoteFont));

        PdfPCell right = new PdfPCell();
        right.setBorderColor(BORDER_COLOR);
        right.setBackgroundColor(PRIMARY_COLOR);
        right.setPadding(10f);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font premLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(191, 219, 254));
        Paragraph pLabel = new Paragraph("NET PAYABLE PREMIUM", premLabelFont);
        pLabel.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pLabel);

        Font premValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.WHITE);
        Paragraph pVal = new Paragraph(formatCurrency(policy.getTotalPremium()) + " VND", premValFont);
        pVal.setAlignment(Element.ALIGN_RIGHT);
        pVal.setSpacingBefore(2f);
        right.addElement(pVal);

        banner.addCell(left);
        banner.addCell(right);
        document.add(banner);
    }

    private void addCertificationFooterBlock(Document document, Policy policy) throws DocumentException {
        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{60f, 40f});
        signTable.setSpacingBefore(10f);

        PdfPCell legalCell = new PdfPCell();
        legalCell.setBorder(Rectangle.NO_BORDER);
        legalCell.setPadding(6f);

        Font legalFont = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
        legalCell.addElement(new Paragraph("DISCLAIMER & CERTIFICATE ATTESTATION:\nThis certificate is issued as a matter of information only and confers no rights upon the certificate holder. This certificate does not affirmatively or negatively amend, extend or alter the coverage afforded by the policy listed herein.", legalFont));

        PdfPCell signatureCell = new PdfPCell();
        signatureCell.setBorder(Rectangle.NO_BORDER);
        signatureCell.setPadding(6f);
        signatureCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font sigTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, PRIMARY_COLOR);
        Paragraph authTitle = new Paragraph("AUTHORIZED UNDERWRITER REPRESENTATIVE", sigTitleFont);
        authTitle.setAlignment(Element.ALIGN_RIGHT);
        signatureCell.addElement(authTitle);

        Font sigNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_DARK);
        Paragraph authName = new Paragraph("Commercial Underwriting Operations", sigNameFont);
        authName.setAlignment(Element.ALIGN_RIGHT);
        authName.setSpacingBefore(18f);
        signatureCell.addElement(authName);

        Font stampFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, TEXT_MUTED);
        Paragraph stamp = new Paragraph("Digitally Certified & Secured System Document", stampFont);
        stamp.setAlignment(Element.ALIGN_RIGHT);
        signatureCell.addElement(stamp);

        signTable.addCell(legalCell);
        signTable.addCell(signatureCell);
        document.add(signTable);
    }

    private void addSummaryTermsAndSignature(Document document, Policy policy) throws DocumentException {
        addCertificationFooterBlock(document, policy);
    }

    private void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE)));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_DARK)));
        cell.setBackgroundColor(bg);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private String formatCurrency(Double amount) {
        if (amount == null) return "0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(amount);
    }

    private String formatDate(Instant instant) {
        if (instant == null) return "—";
        return SHORT_DATE_FORMATTER.format(instant);
    }

    private String safeStr(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "—";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }
}
