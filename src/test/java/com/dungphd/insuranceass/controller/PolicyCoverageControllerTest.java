package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.Coverage;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.PolicyCoverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyCoverageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PolicyCoverageService policyCoverageService;

    @InjectMocks
    private PolicyCoverageController policyCoverageController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyCoverageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCoverages_Success() throws Exception {
        Coverage cov = Coverage.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000.0)
                .premium(750.0)
                .build();

        when(policyCoverageService.getCoverages("pol-1", "1")).thenReturn(List.of(cov));

        mockMvc.perform(get("/policies/pol-1/locations/1/coverages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].coverageCode").value("PROP_FIRE"));
    }

    @Test
    void getCoverageByCode_Success() throws Exception {
        Coverage cov = Coverage.builder()
                .coverageCode("PROP_FIRE")
                .limit(1000000.0)
                .premium(750.0)
                .build();

        when(policyCoverageService.getCoverageByCode("pol-1", "1", "PROP_FIRE")).thenReturn(cov);

        mockMvc.perform(get("/policies/pol-1/locations/1/coverages/PROP_FIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coverageCode").value("PROP_FIRE"));
    }

    @Test
    void addCoverage_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .locations(List.of(Location.builder().locationId(1).build()))
                .totalPremium(750.0)
                .build();

        when(policyCoverageService.addCoverage(eq("pol-1"), eq("1"), any(CoverageDto.class))).thenReturn(policy);

        String jsonPayload = "{\"coverageCode\":\"PROP_FIRE\",\"limit\":1000000.0,\"deductible\":5000.0}";

        mockMvc.perform(post("/policies/pol-1/locations/1/coverages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyNumber").value("POL-2026-001"));
    }

    @Test
    void updateCoverage_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .totalPremium(950.0)
                .build();

        when(policyCoverageService.updateCoverage(eq("pol-1"), eq("1"), eq("PROP_FIRE"), any(CoverageDto.class))).thenReturn(policy);

        String jsonPayload = "{\"coverageCode\":\"PROP_FIRE\",\"limit\":1500000.0}";

        mockMvc.perform(put("/policies/pol-1/locations/1/coverages/PROP_FIRE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPremium").value(950.0));
    }

    @Test
    void removeCoverage_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .totalPremium(0.0)
                .build();

        when(policyCoverageService.removeCoverage("pol-1", "1", "PROP_FIRE")).thenReturn(policy);

        mockMvc.perform(delete("/policies/pol-1/locations/1/coverages/PROP_FIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Coverage removed successfully from location"));
    }
}
