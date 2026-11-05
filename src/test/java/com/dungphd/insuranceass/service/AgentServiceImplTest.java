package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1: CRUD tối thiểu cho Agent — code server sinh, update từng field,
 * không có hard-delete (đóng bằng status CLOSED).
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentCodeGenerator codeGenerator;

    @InjectMocks
    private AgentServiceImpl agentService;

    private Agent agent(String id, String code) {
        return Agent.builder()
                .id(id)
                .code(code)
                .displayName("Đại Lý A")
                .contactEmail("a@example.com")
                .status(AgentStatus.ACTIVE)
                .build();
    }

    @Test
    void listPublicActive_returnsActiveWithSlug_onlyPublicFields() {
        Agent active = agent("agent-1", "AGT-0001");
        active.setSlug("dai-ly-a");
        Agent noSlug = agent("agent-3", "AGT-0003");
        // Query repo đã lọc theo status; service lọc thêm agent chưa có slug
        // (không có link để mở storefront thì nút bấm vô nghĩa).
        when(agentRepository.findByStatusOrderByCodeAsc(AgentStatus.ACTIVE))
                .thenReturn(List.of(active, noSlug));

        List<PublicAgentResponse> result = agentService.listPublicActive();

        assertEquals(1, result.size());
        assertEquals("dai-ly-a", result.get(0).getSlug());
        assertEquals("Đại Lý A", result.get(0).getDisplayName());
        assertEquals("a@example.com", result.get(0).getContactEmail());
        assertEquals(AgentStatus.ACTIVE, result.get(0).getStatus());
        verify(agentRepository).findByStatusOrderByCodeAsc(AgentStatus.ACTIVE);
    }

    @Test
    void create_generatesCodeAndDefaultsToActive() {
        when(codeGenerator.nextCode()).thenReturn("AGT-0001");
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId("agent-1");
            return a;
        });

        AgentResponse response = agentService.createAgent(
                AgentCreateRequest.builder().displayName("  Đại Lý A ").contactEmail("a@example.com").build(),
                "admin@example.com");

        assertEquals("AGT-0001", response.getCode());
        assertEquals("Đại Lý A", response.getDisplayName());
        assertEquals(AgentStatus.ACTIVE, response.getStatus());
        assertEquals("admin@example.com", response.getCreatedBy());
        assertNotNull(response.getCreatedAt());

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertEquals("AGT-0001", captor.getValue().getCode());
        // id không assert ở đây: save-answer của test set id lên cùng instance
        // nên captor thấy cả id đã set; service không tự đặt id (builder không
        // có .id(...)) — đọc code là đủ, không giả lập Mongo sinh id.
    }

    @Test
    void create_blankDisplayName_rejected() {
        assertThrows(BadRequestException.class, () -> agentService.createAgent(
                AgentCreateRequest.builder().displayName("   ").build(), "admin@example.com"));
        verify(agentRepository, never()).save(any());
    }

    @Test
    void create_duplicateCode_retriesWithNewCode() {
        when(codeGenerator.nextCode()).thenReturn("AGT-0001", "AGT-0002");
        when(agentRepository.save(any(Agent.class)))
                .thenThrow(new DuplicateKeyException("dup key"))
                .thenAnswer(i -> i.getArgument(0));

        AgentResponse response = agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").build(), "admin@example.com");

        assertEquals("AGT-0002", response.getCode());
        verify(agentRepository, times(2)).save(any(Agent.class));
        verify(codeGenerator, times(2)).nextCode();
    }

    @Test
    void create_duplicateCodeExhausted_failsClearly() {
        when(codeGenerator.nextCode()).thenReturn("AGT-0001");
        when(agentRepository.save(any(Agent.class)))
                .thenThrow(new DuplicateKeyException("dup key"));

        assertThrows(BadRequestException.class, () -> agentService.createAgent(
                AgentCreateRequest.builder().displayName("Đại Lý A").build(), "admin@example.com"));
        verify(agentRepository, times(AgentServiceImpl.MAX_CODE_ATTEMPTS)).save(any(Agent.class));
    }

    @Test
    void get_notFound_throws() {
        when(agentRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> agentService.getAgent("nope"));
    }

    @Test
    void list_statusFilter_delegates() {
        when(agentRepository.findByStatusOrderByCodeAsc(AgentStatus.SUSPENDED))
                .thenReturn(List.of(agent("a1", "AGT-0001")));
        List<AgentResponse> result = agentService.listAgents(AgentStatus.SUSPENDED);
        assertEquals(1, result.size());
        assertEquals("AGT-0001", result.get(0).getCode());

        when(agentRepository.findAllByOrderByCodeAsc()).thenReturn(List.of());
        assertTrue(agentService.listAgents(null).isEmpty());
    }

    @Test
    void update_partialFields_nullMeansUnchanged_closeByStatus() {
        Agent existing = agent("a1", "AGT-0001");
        existing.setContactEmail("old@example.com");
        when(agentRepository.findById("a1")).thenReturn(Optional.of(existing));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        AgentResponse updated = agentService.updateAgent("a1", AgentUpdateRequest.builder()
                .displayName("Tên Mới")
                .status(AgentStatus.CLOSED)
                .build());

        assertEquals("Tên Mới", updated.getDisplayName());
        assertEquals("old@example.com", updated.getContactEmail(), "field null phải giữ nguyên");
        assertEquals(AgentStatus.CLOSED, updated.getStatus(), "không hard-delete: đóng bằng CLOSED");
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void update_blankDisplayName_rejected() {
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent("a1", "AGT-0001")));
        assertThrows(BadRequestException.class, () -> agentService.updateAgent("a1",
                AgentUpdateRequest.builder().displayName("  ").build()));
        verify(agentRepository, never()).save(any());
    }
}
