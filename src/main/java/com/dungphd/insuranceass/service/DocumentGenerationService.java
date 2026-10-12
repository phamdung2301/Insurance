package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;

import java.util.List;

public interface DocumentGenerationService {

    byte[] generateCertificateOfInsurancePdf(String policyNumber, Integer version);

    byte[] generatePolicySummaryPdf(String policyNumber, Integer version);

    List<DocumentMetadataDto> getAvailableDocuments(String policyNumber);
}
