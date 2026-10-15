package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.DocumentMetadataDto;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.service.DocumentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PolicyDocumentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentGenerationService documentGenerationService;

    @InjectMocks
    private PolicyDocumentController policyDocumentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyDocumentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void downloadCertificate_Success() throws Exception {
        byte[] samplePdf = "%PDF-1.4 Mock Certificate Data".getBytes();

        when(documentGenerationService.generateCertificateOfInsurancePdf(eq("POL-2026-001"), any()))
                .thenReturn(samplePdf);

        mockMvc.perform(get("/policies/POL-2026-001/documents/certificate"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Certificate_POL-2026-001.pdf")))
                .andExpect(content().bytes(samplePdf));
    }

    @Test
    void downloadCertificate_WithVersion_Success() throws Exception {
        byte[] samplePdf = "%PDF-1.4 Mock Certificate Data v2".getBytes();

        when(documentGenerationService.generateCertificateOfInsurancePdf(eq("POL-2026-001"), eq(2)))
                .thenReturn(samplePdf);

        mockMvc.perform(get("/policies/POL-2026-001/documents/certificate").param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Certificate_POL-2026-001_v2.pdf")))
                .andExpect(content().bytes(samplePdf));
    }

    @Test
    void downloadPolicySummary_Success() throws Exception {
        byte[] samplePdf = "%PDF-1.4 Mock Policy Summary Data".getBytes();

        when(documentGenerationService.generatePolicySummaryPdf(eq("POL-2026-001"), any()))
                .thenReturn(samplePdf);

        mockMvc.perform(get("/policies/POL-2026-001/documents/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Policy_Summary_POL-2026-001.pdf")))
                .andExpect(content().bytes(samplePdf));
    }

    @Test
    void getAvailableDocuments_Success() throws Exception {
        DocumentMetadataDto doc1 = DocumentMetadataDto.builder()
                .documentId("COI-1")
                .documentType("CERTIFICATE")
                .title("Certificate of Insurance")
                .fileName("Certificate_POL-2026-001.pdf")
                .policyNumber("POL-2026-001")
                .version(1)
                .contentType("application/pdf")
                .downloadUrl("/policies/POL-2026-001/documents/certificate")
                .generatedAt(Instant.now())
                .build();

        when(documentGenerationService.getAvailableDocuments("POL-2026-001"))
                .thenReturn(List.of(doc1));

        mockMvc.perform(get("/policies/POL-2026-001/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].documentType").value("CERTIFICATE"))
                .andExpect(jsonPath("$.data[0].policyNumber").value("POL-2026-001"));
    }
}
