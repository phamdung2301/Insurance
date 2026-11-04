package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1: DataInitializer seed Agent TRƯỚC rồi mới gán agentId cho
 * agent@example.com. Agent seed dùng mã cố định AGT-SEED (không phụ thuộc
 * counter sinh mã), chạy lại không tạo thêm agent, và không ghi đè agentId
 * mà ADMIN đã gán tay.
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerSeedAgentTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AgentRepository agentRepository;

    private DataInitializer initializer() {
        return new DataInitializer(userRepository, policyRepository, policyVersionRepository,
                policyTransactionRepository, passwordEncoder, agentRepository);
    }

    private void stubCountsNonEmpty() {
        when(userRepository.count()).thenReturn(1L);
        when(policyRepository.count()).thenReturn(1L);
        // Mockito strict-stubbing: mọi findByEmail mà seed gọi đều phải stub
        // tường minh, nếu không runner nuốt PotentialStubbingProblem vào catch
        // và test fail khó hiểu ("save không được gọi").
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("uw@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("finance@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    }

    @Test
    void seed_createsSeedAgentThenAssignsToDemoAgent() throws Exception {
        stubCountsNonEmpty();
        when(agentRepository.findByCode("AGT-SEED")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId("seed-agent-id");
            return a;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        User demoAgent = User.builder().email("agent@example.com")
                .roles(List.of("ROLE_AGENT")).agentId(null).build();
        // Lần 1: seedDemoUser kiểm tra tồn tại -> chưa có (tạo mới).
        // Lần 2: assignSeedAgentToDemoAgent lookup -> thấy user vừa seed.
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.empty(), Optional.of(demoAgent));

        initializer().initData().run();

        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(agentCaptor.capture());
        Agent savedAgent = agentCaptor.getValue();
        assertEquals("AGT-SEED", savedAgent.getCode());
        assertEquals("agent@example.com", savedAgent.getContactEmail());
        assertEquals(AgentStatus.ACTIVE, savedAgent.getStatus());
        assertEquals("seed", savedAgent.getCreatedBy());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertTrue(userCaptor.getAllValues().stream()
                .anyMatch(u -> "agent@example.com".equals(u.getEmail())
                        && "seed-agent-id".equals(u.getAgentId())),
                "agent@example.com phải được gán agentId của agent seed");
    }

    @Test
    void seed_existingSeedAgent_notDuplicated() throws Exception {
        stubCountsNonEmpty();
        // Track C: fixture này là agent ĐÃ có slug (DB đã migrate) → seed chạy
        // lại không save gì thêm. Trường hợp agent cũ chưa có slug được khóa
        // riêng ở test backfill bên dưới (save 1 lần để gán slug, không tạo mới).
        Agent existing = Agent.builder().id("seed-agent-id").code("AGT-SEED").slug("dai-ly-mau").build();
        when(agentRepository.findByCode("AGT-SEED")).thenReturn(Optional.of(existing));
        User demoAgent = User.builder().email("agent@example.com")
                .roles(List.of("ROLE_AGENT")).agentId(null).build();
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(demoAgent));

        initializer().initData().run();

        verify(agentRepository, never()).save(any(Agent.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertTrue(userCaptor.getAllValues().stream()
                .anyMatch(u -> "seed-agent-id".equals(u.getAgentId())));
    }

    @Test
    void seed_existingSeedAgentWithoutSlug_backfillsSampleSlugOnce() throws Exception {
        // Track C: DB seed từ trước khi có slug → seed chạy lại gán slug mẫu
        // "dai-ly-mau" đúng 1 lần (update, không tạo agent mới, không đổi id/code).
        stubCountsNonEmpty();
        Agent existing = Agent.builder().id("seed-agent-id").code("AGT-SEED").build();
        when(agentRepository.findByCode("AGT-SEED")).thenReturn(Optional.of(existing));
        when(agentRepository.existsBySlug("dai-ly-mau")).thenReturn(false);
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
        User demoAgent = User.builder().email("agent@example.com")
                .roles(List.of("ROLE_AGENT")).agentId(null).build();
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(demoAgent));

        initializer().initData().run();

        ArgumentCaptor<Agent> agentCaptor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository, times(1)).save(agentCaptor.capture());
        assertEquals("seed-agent-id", agentCaptor.getValue().getId());
        assertEquals("AGT-SEED", agentCaptor.getValue().getCode());
        assertEquals("dai-ly-mau", agentCaptor.getValue().getSlug());
    }

    @Test
    void seed_doesNotOverwriteAdminAssignedTenant() throws Exception {
        stubCountsNonEmpty();
        Agent existing = Agent.builder().id("seed-agent-id").code("AGT-SEED").build();
        when(agentRepository.findByCode("AGT-SEED")).thenReturn(Optional.of(existing));
        User alreadyAssigned = User.builder().email("agent@example.com")
                .roles(List.of("ROLE_AGENT")).agentId("tenant-other").build();
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(alreadyAssigned));

        initializer().initData().run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertTrue(userCaptor.getAllValues().stream()
                .noneMatch(u -> "agent@example.com".equals(u.getEmail())),
                "user đã có tenant khác thì seed không được save đè");
        assertEquals("tenant-other", alreadyAssigned.getAgentId());
    }
}
