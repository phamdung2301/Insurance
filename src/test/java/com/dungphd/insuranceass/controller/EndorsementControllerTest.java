package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.PolicyVersion;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.service.EndorsementService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EndorsementControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EndorsementService endorsementService;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @InjectMocks
    private EndorsementController endorsementController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(endorsementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void applyEndorsementDirect_WithBodyPolicyNumber_Success() throws Exception {
        EndorsementRequest request = EndorsementRequest.builder()
                .policyNumber("POL-100")
                .reason("Address update")
                .description("Moved to new HQ")
                .build();

        PolicyResponse response = PolicyResponse.builder()
                .policyNumber("POL-100")
                .status(PolicyStatus.ACTIVE)
                .version(2)
                .build();

        when(endorsementService.applyEndorsement(eq("POL-100"), any(EndorsementRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/endorsements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyNumber").value("POL-100"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void getHistoryDirect_Success() throws Exception {
        PolicyTransaction tx = PolicyTransaction.builder()
                .policyNumber("POL-100")
                .version(1)
                .transactionType(TransactionType.CREATE_POLICY)
                .actor("Admin")
                .timestamp(Instant.now())
                .build();

        when(transactionLedgerService.getTransactions("POL-100")).thenReturn(List.of(tx));

        mockMvc.perform(get("/history").param("policyNumber", "POL-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].policyNumber").value("POL-100"))
                .andExpect(jsonPath("$.data[0].version").value(1));
    }

    @Test
    void getVersionsDirect_Success() throws Exception {
        PolicyVersion v1 = PolicyVersion.builder()
                .policyNumber("POL-100")
                .version(1)
                .createdBy("Admin")
                .createdAt(Instant.now())
                .build();

        when(endorsementService.getVersions("POL-100")).thenReturn(List.of(v1));

        mockMvc.perform(get("/versions").param("policyNumber", "POL-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].version").value(1));
    }

    @Test
    void getVersionDirect_Success() throws Exception {
        PolicyVersion v1 = PolicyVersion.builder()
                .policyNumber("POL-100")
                .version(1)
                .createdBy("Admin")
                .createdAt(Instant.now())
                .build();

        when(endorsementService.getVersion("POL-100", 1)).thenReturn(v1);

        mockMvc.perform(get("/versions/1").param("policyNumber", "POL-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(1));
    }
}
