package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.service.AgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 2 Track C: controller public resolve slug — mapping mỏng (service
 * lo phân biệt 404/410), và handler map AgentLinkInactiveException → 410 Gone.
 */
@ExtendWith(MockitoExtension.class)
class PublicAgentControllerTest {

    @Mock
    private AgentService agentService;

    @InjectMocks
    private PublicAgentController controller;

    private PublicAgentResponse storefront() {
        return PublicAgentResponse.builder()
                .displayName("Đại Lý Mẫu").contactName("Người Liên Hệ")
                .contactEmail("a@example.com").phone("0900000000").address("123 Đường A")
                .status(AgentStatus.ACTIVE).build();
    }

    @Test
    void resolve_active_returns200_withStorefrontData() {
        when(agentService.resolvePublicBySlug("dai-ly-mau")).thenReturn(storefront());

        var entity = controller.resolveBySlug("dai-ly-mau");

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertTrue(entity.getBody().isSuccess());
        assertEquals("Đại Lý Mẫu", entity.getBody().getData().getDisplayName());
        assertEquals(AgentStatus.ACTIVE, entity.getBody().getData().getStatus());
        verify(agentService).resolvePublicBySlug("dai-ly-mau");
    }

    @Test
    void inactiveSlug_mapsTo410Gone_not404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var entity = handler.handleAgentLinkInactive(
                new AgentLinkInactiveException("dai-ly-treo", AgentStatus.SUSPENDED));
        assertEquals(HttpStatus.GONE, entity.getStatusCode());
        assertFalse(entity.getBody().isSuccess());
    }
}
