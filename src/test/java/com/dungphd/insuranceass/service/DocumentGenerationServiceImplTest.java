package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.DocumentGenerationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    @InjectMocks
    private DocumentGenerationServiceImpl documentGenerationService;

    private Policy samplePolicy;

    @BeforeEach
    void setUp() {
        Coverage coverage1 = Coverage.builder()
                .coverageCode("PROPERTY")
                .coverageName("Property & Fire Protection")
                .coverageType("STANDARD")
                .limit(500000000.0)
                .deductible(10000000.0)
                .termMonths(12)
                .baseRate(0.075)
                .premium(375000.0)
                .build();

        Coverage coverage2 = Coverage.builder()
                .coverageCode("GL")
                .coverageName("General Liability")
                .coverageType("ENHANCED")
                .limit(200000000.0)
                .deductible(5000000.0)
                .termMonths(12)
                .baseRate(0.12)
                .premium(240000.0)
                .build();

        Location location = Location.builder()
                .locationId(1)
                .address("123 Nguyen Hue, Ben Nghe, Q1, HCMC")
                .coverages(new ArrayList<>(List.of(coverage1, coverage2)))
                .build();

        Insured insured = Insured.builder()
                .insuredId("INS-001")
                .name("Global Enterprise Solutions Corp")
                .type("CORPORATION")
                .email("contact@globalsolutions.vn")
                .phone("02838229988")
                .address("123 Nguyen Hue, Ben Nghe, Q1, HCMC")
                .build();

        samplePolicy = Policy.builder()
                .id("pol-uuid-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.ACTIVE)
                .insured(insured)
                .locations(new ArrayList<>(List.of(location)))
                .totalPremium(615000.0)
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now().plus(365, ChronoUnit.DAYS))
                .boundDate(Instant.now())
                .paymentDueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void generateCertificateOfInsurancePdf_Success() {
        when(policyRepository.findByPolicyNumber("POL-2026-001")).thenReturn(Optional.of(samplePolicy));

        byte[] pdfBytes = documentGenerationService.generateCertificateOfInsurancePdf("POL-2026-001", null);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // Verify PDF Header Magic Bytes (%PDF-)
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void generateCertificateOfInsurancePdf_HistoricalVersion_Success() {
        samplePolicy.setVersion(2);
        when(policyRepository.findByPolicyNumber("POL-2026-001")).thenReturn(Optional.of(samplePolicy));

        Policy snap1 = Policy.builder()
                .policyNumber("POL-2026-001")
                .version(1)
                .status(PolicyStatus.BOUND)
                .insured(samplePolicy.getInsured())
                .locations(samplePolicy.getLocations())
                .totalPremium(500000.0)
                .build();

        PolicyVersion v1 = PolicyVersion.builder()
                .policyNumber("POL-2026-001")
                .version(1)
                .policySnapshot(snap1)
                .build();

        when(policyVersionRepository.findByPolicyNumberAndVersion("POL-2026-001", 1))
                .thenReturn(Optional.of(v1));

        byte[] pdfBytes = documentGenerationService.generateCertificateOfInsurancePdf("POL-2026-001", 1);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generatePolicySummaryPdf_Success() {
        when(policyRepository.findByPolicyNumber("POL-2026-001")).thenReturn(Optional.of(samplePolicy));

        byte[] pdfBytes = documentGenerationService.generatePolicySummaryPdf("POL-2026-001", null);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void generatePdf_NotFound_ThrowsResourceNotFound() {
        when(policyRepository.findByPolicyNumber("POL-UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                documentGenerationService.generateCertificateOfInsurancePdf("POL-UNKNOWN", null));
    }

    @Test
    void getAvailableDocuments_ReturnsDocumentMetadataList() {
        when(policyRepository.findByPolicyNumber("POL-2026-001")).thenReturn(Optional.of(samplePolicy));

        List<DocumentMetadataDto> docs = documentGenerationService.getAvailableDocuments("POL-2026-001");

        assertNotNull(docs);
        assertEquals(2, docs.size());
        assertEquals("CERTIFICATE", docs.get(0).getDocumentType());
        assertEquals("SUMMARY", docs.get(1).getDocumentType());
        assertTrue(docs.get(0).getDownloadUrl().contains("certificate"));
    }
}
