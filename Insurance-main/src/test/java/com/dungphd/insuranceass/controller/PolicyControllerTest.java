package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.service.PolicyService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PolicyService policyService;

    @InjectMocks
    private PolicyController policyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchPolicies_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .totalPremium(500000.0)
                .build();

        PageResponse<Policy> pageResponse = PageResponse.<Policy>builder()
                .content(List.of(policy))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(policyService.searchPolicies(any(PolicySearchCriteria.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/policies")
                        .param("status", "DRAFT")
                        .param("insuredName", "Global")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].policyNumber").value("POL-2026-001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getPolicyById_Success() throws Exception {
        Policy policy = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-001")
                .status(PolicyStatus.DRAFT)
                .build();

        when(policyService.getPolicyById("pol-1")).thenReturn(policy);

        mockMvc.perform(get("/policies/pol-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyNumber").value("POL-2026-001"));
    }

    @Test
    void createPolicy_Success() throws Exception {
        Policy created = Policy.builder()
                .id("pol-1")
                .policyNumber("POL-2026-002")
                .status(PolicyStatus.DRAFT)
                .build();

        when(policyService.createPolicy(any(Policy.class))).thenReturn(created);

        String jsonPayload = "{\"policyNumber\":\"POL-2026-002\"}";

        mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyNumber").value("POL-2026-002"));
    }
}
