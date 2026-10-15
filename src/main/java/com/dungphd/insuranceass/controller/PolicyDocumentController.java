package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;
import com.dungphd.insuranceass.service.DocumentGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping({"/policies/{policyNumber}/documents", "/api/policies/{policyNumber}/documents"})
@RequiredArgsConstructor
public class PolicyDocumentController {

    private final DocumentGenerationService documentGenerationService;

    /**
     * GET /policies/{policyNumber}/documents
     * Retrieve available downloadable document list and metadata for the policy
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentMetadataDto>>> getAvailableDocuments(
            @PathVariable("policyNumber") String policyNumber
    ) {
        List<DocumentMetadataDto> docs = documentGenerationService.getAvailableDocuments(policyNumber);
        return ResponseEntity.ok(ApiResponse.success(docs, "Available policy documents retrieved successfully"));
    }

    /**
     * GET /policies/{policyNumber}/documents/certificate
     * Download Certificate of Insurance (COI) PDF document
     * Query param: ?version={v} (optional)
     */
    @GetMapping("/certificate")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable("policyNumber") String policyNumber,
            @RequestParam(value = "version", required = false) Integer version
    ) {
        byte[] pdfBytes = documentGenerationService.generateCertificateOfInsurancePdf(policyNumber, version);

        String verSuffix = version != null ? "_v" + version : "";
        String filename = "Certificate_" + policyNumber + verSuffix + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * GET /policies/{policyNumber}/documents/summary
     * Download Policy Schedule & Summary PDF document
     * Query param: ?version={v} (optional)
     */
    @GetMapping("/summary")
    public ResponseEntity<byte[]> downloadPolicySummary(
            @PathVariable("policyNumber") String policyNumber,
            @RequestParam(value = "version", required = false) Integer version
    ) {
        byte[] pdfBytes = documentGenerationService.generatePolicySummaryPdf(policyNumber, version);

        String verSuffix = version != null ? "_v" + version : "";
        String filename = "Policy_Summary_" + policyNumber + verSuffix + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
