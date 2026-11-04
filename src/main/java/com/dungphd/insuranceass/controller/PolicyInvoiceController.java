package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.service.DocumentGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * GET /policies/{policyNumber}/invoice (dot 3B).
 *
 * <p>Nam o controller rieng (khong phai PolicyDocumentController) vi controller
 * kia map class-level vao "/policies/{policyNumber}/documents" nen khong the
 * phuc vu path "/policies/{policyNumber}/invoice" ma khong pha route hien co.
 * Endpoint nay nam trong matcher "/policies/**" authenticated san co
 * (SecurityConfig) va di qua dung checkPolicyRead nhu certificate/summary:
 * service generateInvoicePdf goi permissionService.checkPolicyRead nen
 * customer chi tai duoc invoice cua chinh minh (customer khac -> 403).
 *
 * <p>Invoice chi la chung tu ghi nhan, khong phai luong tien that.
 */
@RestController
@RequestMapping({"/policies/{policyNumber}", "/api/policies/{policyNumber}"})
@RequiredArgsConstructor
public class PolicyInvoiceController {

    private final DocumentGenerationService documentGenerationService;

    @GetMapping("/invoice")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable("policyNumber") String policyNumber
    ) {
        byte[] pdfBytes = documentGenerationService.generateInvoicePdf(policyNumber);

        String filename = "Invoice_" + policyNumber + ".pdf";

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
