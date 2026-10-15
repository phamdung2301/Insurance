package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.CalculatePremiumRequest;
import com.dungphd.insuranceass.dto.response.PremiumCalculationResultDto;
import com.dungphd.insuranceass.dto.response.RateMatrixItemDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface PricingExcelService {

    /**
     * FR-CALC-02: Export/Download sample Excel pricing matrix
     * (InsurancePricingMatrix_{Date}.xlsx) with preconfigured formulas and base-rate catalog.
     */
    byte[] generatePricingTemplate();

    /**
     * FR-CALC-03: Upload an Excel file to update all base rates in the system's in-memory pricing table.
     */
    Map<String, Object> importRatesFromExcel(MultipartFile file);

    /**
     * FR-CALC-05: Real-time instant premium calculation without creating a database policy.
     */
    PremiumCalculationResultDto calculateIndicativePremium(CalculatePremiumRequest request);

    /**
     * FR-CALC-04: Export professionally formatted Excel breakdown of the premium calculation
     * for an individual policy (PremiumCalc_{PolicyNumber}.xlsx), including digital signatures.
     */
    byte[] exportPolicyPremiumBreakdown(String policyNumber);

    /**
     * Retrieve the current active rate matrix catalog.
     */
    List<RateMatrixItemDto> getCurrentRateCatalog();
}
