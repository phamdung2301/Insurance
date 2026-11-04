package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.service.AgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1: controller CRUD /admin/agents — unit test ở tầng controller
 * (service mock). Quyền ADMIN-only được enforce bởi matcher /admin/** trong
 * SecurityConfig (không phải controller tự check), nên test này khóa mapping
 * request/response + status code, không giả lập SecurityConfig.
 */
@ExtendWith(MockitoExtension.class)
class AdminAgentControllerTest {

    @Mock
    private AgentService agentService;

    @InjectMocks
    private AdminAgentController controller;

    private AgentResponse response() {
        return AgentResponse.builder()
                .id("a1").code("AGT-0001").displayName("Đại Lý A").status(AgentStatus.ACTIVE)
                .build();
    }

    @Test
    void create_returns201_withActorFromPrincipal() {
        when(agentService.createAgent(any(), eq("admin@example.com"))).thenReturn(response());
        Principal principal = () -> "admin@example.com";

        var entity = controller.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").build(), principal);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertTrue(entity.getBody().isSuccess());
        assertEquals("AGT-0001", entity.getBody().getData().getCode());
        verify(agentService).createAgent(any(), eq("admin@example.com"));
    }

    @Test
    void list_passesStatusFilter() {
        when(agentService.listAgents(AgentStatus.SUSPENDED)).thenReturn(List.of(response()));
        var entity = controller.listAgents(AgentStatus.SUSPENDED);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertEquals(1, entity.getBody().getData().size());
    }

    @Test
    void getAndUpdate_delegate() {
        when(agentService.getAgent("a1")).thenReturn(response());
        assertEquals("AGT-0001", controller.getAgent("a1").getBody().getData().getCode());

        when(agentService.updateAgent(eq("a1"), any())).thenReturn(response());
        var updated = controller.updateAgent("a1",
                AgentUpdateRequest.builder().status(AgentStatus.CLOSED).build());
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        verify(agentService).updateAgent(eq("a1"), any());
    }
}
