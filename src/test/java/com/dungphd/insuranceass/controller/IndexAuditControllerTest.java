package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.IndexAuditReport;
import com.dungphd.insuranceass.service.MongoIndexAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IndexAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MongoIndexAuditService mongoIndexAuditService;

    @InjectMocks
    private IndexAuditController indexAuditController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(indexAuditController).build();
    }

    @Test
    void auditIndexes_ReturnsReportSuccessfully() throws Exception {
        IndexAuditReport mockReport = IndexAuditReport.builder()
                .status("OPTIMIZED")
                .auditTimestamp(Instant.now())
                .totalCollectionsAudited(4)
                .totalIndexes(12)
                .totalMultikeyIndexes(5)
                .totalCompoundIndexes(6)
                .redundantIndexesDetected(0)
                .collections(Collections.emptyList())
                .recommendations(List.of("Tuned for $elemMatch"))
                .build();

        when(mongoIndexAuditService.auditIndexes()).thenReturn(mockReport);

        mockMvc.perform(get("/admin/indexes/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OPTIMIZED"))
                .andExpect(jsonPath("$.data.totalIndexes").value(12))
                .andExpect(jsonPath("$.data.totalMultikeyIndexes").value(5));
    }

    @Test
    void syncIndexes_TriggersSyncSuccessfully() throws Exception {
        doNothing().when(mongoIndexAuditService).ensureOptimizedIndexes();

        mockMvc.perform(post("/admin/indexes/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Index sync successful"));
    }
}
