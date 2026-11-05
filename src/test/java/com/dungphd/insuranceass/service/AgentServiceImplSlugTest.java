package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 2 Track C: slug ở AgentService — chuẩn hóa chữ thường, pattern,
 * trùng → 400, và resolve public phân biệt 404 / 410 / 200.
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplSlugTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentCodeGenerator codeGenerator;

    @InjectMocks
    private AgentServiceImpl agentService;

    private Agent agent(String id, String slug, AgentStatus status) {
        return Agent.builder()
                .id(id).code("AGT-0001").slug(slug).displayName("Đại Lý Mẫu")
                .contactName("Người Liên Hệ").contactEmail("a@example.com")
                .phone("0900000000").address("123 Đường A").status(status)
                .build();
    }

    @Test
    void create_normalizesSlugToLowercase() {
        when(codeGenerator.nextCode()).thenReturn("AGT-0001");
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId("agent-1");
            return a;
        });

        AgentResponse response = agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").slug("  Dai-Ly-Mau  ").build(),
                "admin@example.com");

        assertEquals("dai-ly-mau", response.getSlug());
        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertEquals("dai-ly-mau", captor.getValue().getSlug());
    }

    @Test
    void create_invalidSlugPattern_rejected() {
        assertThrows(BadRequestException.class, () -> agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").slug("dai_ly").build(),
                "admin@example.com"));
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void create_duplicateSlug_rejected() {
        when(agentRepository.findBySlug("dai-ly-mau"))
                .thenReturn(Optional.of(agent("other", "dai-ly-mau", AgentStatus.ACTIVE)));

        assertThrows(BadRequestException.class, () -> agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý B").slug("dai-ly-mau").build(),
                "admin@example.com"));
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void create_withoutSlug_allowed_nullStaysNull() {
        when(codeGenerator.nextCode()).thenReturn("AGT-0001");
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId("agent-1");
            return a;
        });

        AgentResponse response = agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").build(),
                "admin@example.com");
        assertNull(response.getSlug());
    }

    @Test
    void update_setsSlugNormalized_andSameSlugOwnAgentAllowed() {
        Agent self = agent("agent-1", "dai-ly-cu", AgentStatus.ACTIVE);
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(self));
        when(agentRepository.findBySlug("dai-ly-moi")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        AgentResponse response = agentService.updateAgent("agent-1",
                AgentUpdateRequest.builder().slug("DAI-LY-MOI").build());
        assertEquals("dai-ly-moi", response.getSlug());
    }

    @Test
    void update_duplicateSlugOfOtherAgent_rejected() {
        Agent self = agent("agent-1", "dai-ly-cu", AgentStatus.ACTIVE);
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(self));
        when(agentRepository.findBySlug("dai-ly-mau"))
                .thenReturn(Optional.of(agent("other", "dai-ly-mau", AgentStatus.ACTIVE)));

        assertThrows(BadRequestException.class, () -> agentService.updateAgent("agent-1",
                AgentUpdateRequest.builder().slug("dai-ly-mau").build()));
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void update_blankSlug_rejected_notSilentClear() {
        Agent self = agent("agent-1", "dai-ly-cu", AgentStatus.ACTIVE);
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(self));

        assertThrows(BadRequestException.class, () -> agentService.updateAgent("agent-1",
                AgentUpdateRequest.builder().slug("   ").build()));
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void resolve_active_returnsOnlySafeFields() {
        when(agentRepository.findBySlug("dai-ly-mau"))
                .thenReturn(Optional.of(agent("agent-1", "dai-ly-mau", AgentStatus.ACTIVE)));

        PublicAgentResponse response = agentService.resolvePublicBySlug("dai-ly-mau");
        assertEquals("Đại Lý Mẫu", response.getDisplayName());
        assertEquals("Người Liên Hệ", response.getContactName());
        assertEquals("a@example.com", response.getContactEmail());
        assertEquals("0900000000", response.getPhone());
        assertEquals("123 Đường A", response.getAddress());
        assertEquals(AgentStatus.ACTIVE, response.getStatus());
        // PublicAgentResponse không có field id/code/createdBy — kiểm chứng ở
        // cấp compile (DTO không khai báo chúng), không assert được field
        // không tồn tại ở runtime.
    }

    @Test
    void resolve_unknownSlug_throws404() {
        when(agentRepository.findBySlug("khong-co")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> agentService.resolvePublicBySlug("khong-co"));
    }

    @Test
    void resolve_suspended_throws410_distinctFrom404() {
        when(agentRepository.findBySlug("dai-ly-treo"))
                .thenReturn(Optional.of(agent("agent-2", "dai-ly-treo", AgentStatus.SUSPENDED)));
        AgentLinkInactiveException ex = assertThrows(AgentLinkInactiveException.class,
                () -> agentService.resolvePublicBySlug("dai-ly-treo"));
        assertEquals(AgentStatus.SUSPENDED, ex.getStatus());
    }

    @Test
    void resolve_closed_throws410() {
        when(agentRepository.findBySlug("dai-ly-dong"))
                .thenReturn(Optional.of(agent("agent-3", "dai-ly-dong", AgentStatus.CLOSED)));
        AgentLinkInactiveException ex = assertThrows(AgentLinkInactiveException.class,
                () -> agentService.resolvePublicBySlug("dai-ly-dong"));
        assertEquals(AgentStatus.CLOSED, ex.getStatus());
    }
}
