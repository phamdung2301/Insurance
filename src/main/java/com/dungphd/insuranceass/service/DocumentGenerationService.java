package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;

import java.util.List;

public interface DocumentGenerationService {

    byte[] generateCertificateOfInsurancePdf(String policyNumber, Integer version);

    byte[] generatePolicySummaryPdf(String policyNumber, Integer version);

    /**
     * Hoa don (invoice) cho mot hop dong, theo dung pattern certificate/summary:
     * resolve policy -> checkPolicyRead (customer chi tai duoc invoice cua chinh
     * minh) -> render PDF. Invoice CHI LA CHUNG TU GHI NHAN (record document):
     * no khong phai luong tien that, khong chuyen tien, khong thay doi trang
     * thai thanh toan — trang thai thanh toan chi phan anh paymentStatus /
     * paymentClaimedAt da ghi nhan tren policy. Cau nay cung duoc in trong
     * noi dung PDF (xem DocumentGenerationServiceImpl).
     */
    byte[] generateInvoicePdf(String policyNumber);

    List<DocumentMetadataDto> getAvailableDocuments(String policyNumber);
}
