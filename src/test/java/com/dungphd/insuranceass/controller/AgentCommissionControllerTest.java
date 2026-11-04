package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.AgentFunnelResponse;
import com.dungphd.insuranceass.dto.response.CommissionListResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.CommissionStatus;
import com.dungphd.insuranceass.service.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đợt 3A — controller hoa hồng agent (mẫu NotificationControllerTest:
 * MockMvc standalone, service mock). Scope thật (AGENT/ADMIN/403) được
 * kiểm ở CommissionServiceImplTest; ở đây kiểm controller truyền đúng
 * principal từ JWT và map đúng status HTTP (401 không principal, 403
 * khi service throw ForbiddenException).
 */
@ExtendWith(MockitoExtension.class)
class AgentCommissionControllerTest {

    @Mock
    private CommissionService commissionService;

    private MockMvc mockMvc;
    private MockMvc mockMvcNoAdvice;
    private final Principal principal = () -> "agent@example.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentCommissionController(commissionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mockMvcNoAdvice = MockMvcBuilders.standaloneSetup(new AgentCommissionController(commissionService))
                .build();
    }

    @Test
    void list_returnsItemsAndTotalsFromService() throws Exception {
        CommissionListResponse res = CommissionListResponse.builder()
                .items(List.of(Commission.builder().policyNumber("POL-001")
                        .agentEmail("agent@example.com").amount(50_000.0)
                        .status(CommissionStatus.PENDING).build()))
                .totalPending(50_000.0)
                .totalEarned(0.0)
                .build();
        when(commissionService.listForPrincipal("agent@example.com")).thenReturn(res);

        mockMvc.perform(get("/agent/commissions").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].policyNumber").value("POL-001"))
                .andExpect(jsonPath("$.data.totalPending").value(50_000.0))
                .andExpect(jsonPath("$.data.totalEarned").value(0.0));
    }

    @Test
    void list_forbiddenFromService_mapsTo403() throws Exception {
        when(commissionService.listForPrincipal("agent@example.com"))
                .thenThrow(new ForbiddenException("Chỉ đại lý (AGENT) hoặc ADMIN mới được xem hoa hồng."));

        mockMvc.perform(get("/agent/commissions").principal(principal))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_noPrincipal_returns401() throws Exception {
        mockMvcNoAdvice.perform(get("/agent/commissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void funnel_returnsCountsFromService() throws Exception {
        AgentFunnelResponse res = AgentFunnelResponse.builder()
                .agentEmail("agent@example.com").draft(2).quoted(3).bound(1).active(4).build();
        when(commissionService.funnelForPrincipal("agent@example.com")).thenReturn(res);

        mockMvc.perform(get("/agent/commissions/funnel").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft").value(2))
                .andExpect(jsonPath("$.data.quoted").value(3))
                .andExpect(jsonPath("$.data.bound").value(1))
                .andExpect(jsonPath("$.data.active").value(4))
                .andExpect(jsonPath("$.data.total").value(10));
    }

    @Test
    void funnel_noPrincipal_returns401() throws Exception {
        mockMvcNoAdvice.perform(get("/agent/commissions/funnel"))
                .andExpect(status().isUnauthorized());
    }
}
