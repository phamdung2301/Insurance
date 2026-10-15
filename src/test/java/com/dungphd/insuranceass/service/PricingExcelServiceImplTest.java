package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CalculatePremiumRequest;
import com.dungphd.insuranceass.dto.response.PremiumCalculationResultDto;
import com.dungphd.insuranceass.dto.response.RateMatrixItemDto;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.PricingExcelServiceImpl;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingExcelServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    private PricingExcelServiceImpl pricingExcelService;

    @BeforeEach
    void setUp() {
        pricingExcelService = new PricingExcelServiceImpl(policyRepository);
    }

    @Test
    void generatePricingTemplate_Success() throws Exception {
        byte[] bytes = pricingExcelService.generatePricingTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals("Pricing_Matrix", wb.getSheetAt(0).getSheetName());
            assertEquals("Calculation_Formulas", wb.getSheetAt(1).getSheetName());
            assertTrue(wb.getSheetAt(0).getLastRowNum() >= 6);
        }
    }

    @Test
    void calculateIndicativePremium_Success() {
        CalculatePremiumRequest req = CalculatePremiumRequest.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000000.0)
                .deductible(5000000.0)
                .termMonths(12)
                .riskFactor(1.0)
                .build();

        PremiumCalculationResultDto res = pricingExcelService.calculateIndicativePremium(req);
        assertNotNull(res);
        assertEquals("PROP_FIRE", res.getCoverageCode());
        assertEquals(0.0015, res.getBaseRate());
        assertEquals(1500000.0, res.getCalculatedPremium());
    }

    @Test
    void importRatesFromExcel_Success() {
        byte[] templateBytes = pricingExcelService.generatePricingTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file", "matrix.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        Map<String, Object> result = pricingExcelService.importRatesFromExcel(file);
        assertNotNull(result);
        assertTrue((int) result.get("updatedCount") >= 6);
    }

    @Test
    void exportPolicyPremiumBreakdown_Success() throws Exception {
        Policy policy = Policy.builder()
                .policyNumber("POL-2026-TEST")
                .status(PolicyStatus.ACTIVE)
                .version(1)
                .totalPremium(1500000.0)
                .insured(Insured.builder()
                        .name("Công ty ABC")
                        .email("abc@test.vn")
                        .phone("0912345678")
                        .address("123 Phố Huế, Hà Nội")
                        .build())
                .locations(List.of(
                        Location.builder()
                                .locationId(1)
                                .address("456 Giải Phóng, Hà Nội")
                                .coverages(List.of(
                                        Coverage.builder()
                                                .coverageCode("PROP_FIRE")
                                                .coverageName("Bảo hiểm Cháy nổ")
                                                .limit(1000000000.0)
                                                .deductible(5000000.0)
                                                .termMonths(12)
                                                .premium(1500000.0)
                                                .build()
                                ))
                                .build()
                ))
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now().plusSeconds(31536000))
                .build();

        when(policyRepository.findByPolicyNumber("POL-2026-TEST")).thenReturn(Optional.of(policy));

        byte[] bytes = pricingExcelService.exportPolicyPremiumBreakdown("POL-2026-TEST");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals("Premium_Breakdown", wb.getSheetAt(0).getSheetName());
        }
    }

    @Test
    void getCurrentRateCatalog_ReturnsAllRates() {
        List<RateMatrixItemDto> catalog = pricingExcelService.getCurrentRateCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.size() >= 6);
    }
}
