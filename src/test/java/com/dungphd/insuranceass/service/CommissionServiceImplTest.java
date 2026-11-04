package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.AgentFunnelResponse;
import com.dungphd.insuranceass.dto.response.CommissionListResponse;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.CommissionStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.repository.CommissionRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.CommissionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Đợt 3A — hoa hồng agent.
 * PermissionService thật + SecurityContextHolder thật (mẫu
 * UnderwriterQueueServiceImplTest): không mock permission, để fail-closed
 * và scope AGENT/ADMIN được kiểm chứng thật. Repository mock.
 */
@ExtendWith(MockitoExtension.class)
class CommissionServiceImplTest {

    @Mock
    private CommissionRepository commissionRepository;

    @Mock
    private PolicyRepository policyRepository;

    private final PermissionService permissionService = new PermissionService();

    private CommissionServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new CommissionServiceImpl(commissionRepository, policyRepository, permissionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    private Policy policy(String number, String agentEmail, Double premium) {
        return Policy.builder()
                .policyNumber(number)
                .assignedAgentEmail(agentEmail)
                .totalPremium(premium)
                .build();
    }

    // ---- createForBound ----

    @Test
    void createForBound_createsExactlyFivePercent() {
        when(commissionRepository.existsByPolicyNumber("POL-001")).thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.createForBound(policy("POL-001", "agent@example.com", 1_000_000.0));

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        Commission saved = captor.getValue();
        assertEquals("POL-001", saved.getPolicyNumber());
        assertEquals("agent@example.com", saved.getAgentEmail());
        assertEquals(50_000.0, saved.getAmount());
        assertEquals(CommissionStatus.PENDING, saved.getStatus());
        assertNull(saved.getEarnedAt());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void createForBound_roundsToWholeDongHalfUp() {
        when(commissionRepository.existsByPolicyNumber("POL-002")).thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 5% của 333.333 = 16.666,65 → đồng nguyên HALF_UP = 16.667.
        service.createForBound(policy("POL-002", "agent@example.com", 333_333.0));

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        assertEquals(16_667.0, captor.getValue().getAmount());
    }

    @Test
    void createForBound_isIdempotent_secondCallCreatesNothing() {
        when(commissionRepository.existsByPolicyNumber("POL-001")).thenReturn(true);

        service.createForBound(policy("POL-001", "agent@example.com", 1_000_000.0));
        service.createForBound(policy("POL-001", "agent@example.com", 1_000_000.0));

        verify(commissionRepository, never()).save(any());
    }

    @Test
    void createForBound_noAgent_skipsSilently() {
        service.createForBound(policy("POL-001", null, 1_000_000.0));
        service.createForBound(policy("POL-001", "   ", 1_000_000.0));
        service.createForBound(null);

        verify(commissionRepository, never()).save(any());
    }

    @Test
    void createForBound_repositoryFailure_doesNotThrow() {
        when(commissionRepository.existsByPolicyNumber("POL-001")).thenReturn(false);
        when(commissionRepository.save(any())).thenThrow(new RuntimeException("mongo down"));

        assertDoesNotThrow(() -> service.createForBound(policy("POL-001", "agent@example.com", 100.0)));
    }

    // ---- markEarned ----

    @Test
    void markEarned_pendingBecomesEarnedWithTimestamp() {
        Commission pending = Commission.builder()
                .policyNumber("POL-001")
                .agentEmail("agent@example.com")
                .amount(50_000.0)
                .status(CommissionStatus.PENDING)
                .createdAt(Instant.now())
                .earnedAt(null)
                .build();
        when(commissionRepository.findByPolicyNumber("POL-001")).thenReturn(Optional.of(pending));
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.markEarned(policy("POL-001", "agent@example.com", 1_000_000.0));

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        assertEquals(CommissionStatus.EARNED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getEarnedAt());
    }

    @Test
    void markEarned_noRecord_doesNothingAndDoesNotThrow() {
        when(commissionRepository.findByPolicyNumber("POL-001")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.markEarned(policy("POL-001", "agent@example.com", 100.0)));

        verify(commissionRepository, never()).save(any());
    }

    @Test
    void markEarned_alreadyEarned_keepsOriginalEarnedAt() {
        Instant earnedAt = Instant.parse("2026-01-02T03:04:05Z");
        Commission earned = Commission.builder()
                .policyNumber("POL-001")
                .status(CommissionStatus.EARNED)
                .earnedAt(earnedAt)
                .build();
        when(commissionRepository.findByPolicyNumber("POL-001")).thenReturn(Optional.of(earned));

        service.markEarned(policy("POL-001", "agent@example.com", 100.0));

        verify(commissionRepository, never()).save(any());
        assertEquals(earnedAt, earned.getEarnedAt());
    }

    @Test
    void markEarned_repositoryFailure_doesNotThrow() {
        when(commissionRepository.findByPolicyNumber("POL-001"))
                .thenThrow(new RuntimeException("mongo down"));

        assertDoesNotThrow(() -> service.markEarned(policy("POL-001", "agent@example.com", 100.0)));
    }

    // ---- scope: list ----

    @Test
    void listForPrincipal_agentSeesOnlyOwnEvenIfArgumentDiffers() {
        authenticateAs("agent-a@example.com", "ROLE_AGENT");
        Commission own = Commission.builder().agentEmail("agent-a@example.com")
                .amount(10.0).status(CommissionStatus.PENDING).build();
        when(commissionRepository.findByAgentEmailOrderByCreatedAtDesc("agent-a@example.com"))
                .thenReturn(List.of(own));

        // Đối số truyền email khác — principal thật trong SecurityContext phải thắng.
        CommissionListResponse res = service.listForPrincipal("agent-b@example.com");

        verify(commissionRepository).findByAgentEmailOrderByCreatedAtDesc("agent-a@example.com");
        verify(commissionRepository, never()).findAllByOrderByCreatedAtDesc();
        assertEquals(1, res.getItems().size());
        assertEquals(10.0, res.getTotalPending());
        assertEquals(0.0, res.getTotalEarned());
    }

    @Test
    void listForPrincipal_sumsPendingAndEarnedSeparately() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        when(commissionRepository.findByAgentEmailOrderByCreatedAtDesc("agent@example.com"))
                .thenReturn(List.of(
                        Commission.builder().amount(100.0).status(CommissionStatus.PENDING).build(),
                        Commission.builder().amount(50.0).status(CommissionStatus.EARNED).build(),
                        Commission.builder().amount(null).status(CommissionStatus.PENDING).build()));

        CommissionListResponse res = service.listForPrincipal("agent@example.com");

        assertEquals(100.0, res.getTotalPending());
        assertEquals(50.0, res.getTotalEarned());
    }

    @Test
    void listForPrincipal_adminSeesAll() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        when(commissionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                Commission.builder().agentEmail("a@example.com").amount(5.0)
                        .status(CommissionStatus.EARNED).build(),
                Commission.builder().agentEmail("b@example.com").amount(7.0)
                        .status(CommissionStatus.PENDING).build()));

        CommissionListResponse res = service.listForPrincipal("admin@example.com");

        verify(commissionRepository).findAllByOrderByCreatedAtDesc();
        assertEquals(2, res.getItems().size());
        assertEquals(7.0, res.getTotalPending());
        assertEquals(5.0, res.getTotalEarned());
    }

    @Test
    void listForPrincipal_customerForbidden() {
        authenticateAs("customer@example.com", "ROLE_CUSTOMER");

        assertThrows(ForbiddenException.class, () -> service.listForPrincipal("customer@example.com"));
        verify(commissionRepository, never()).findByAgentEmailOrderByCreatedAtDesc(any());
        verify(commissionRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void listForPrincipal_noPrincipalForbidden() {
        assertThrows(ForbiddenException.class, () -> service.listForPrincipal("someone@example.com"));
    }

    // ---- scope: funnel ----

    @Test
    void funnelForPrincipal_agentCountsOwnPoliciesOnly() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        when(policyRepository.countByAssignedAgentEmailAndStatus("agent@example.com", PolicyStatus.DRAFT))
                .thenReturn(2L);
        when(policyRepository.countByAssignedAgentEmailAndStatus("agent@example.com", PolicyStatus.QUOTED))
                .thenReturn(3L);
        when(policyRepository.countByAssignedAgentEmailAndStatus("agent@example.com", PolicyStatus.BOUND))
                .thenReturn(1L);
        when(policyRepository.countByAssignedAgentEmailAndStatus("agent@example.com", PolicyStatus.ACTIVE))
                .thenReturn(4L);

        AgentFunnelResponse res = service.funnelForPrincipal("agent@example.com");

        assertEquals("agent@example.com", res.getAgentEmail());
        assertEquals(2, res.getDraft());
        assertEquals(3, res.getQuoted());
        assertEquals(1, res.getBound());
        assertEquals(4, res.getActive());
        assertEquals(10, res.getTotal());
        verify(policyRepository, never()).countByStatus(any());
    }

    @Test
    void funnelForPrincipal_adminCountsAllPolicies() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        when(policyRepository.countByStatus(PolicyStatus.DRAFT)).thenReturn(5L);
        when(policyRepository.countByStatus(PolicyStatus.QUOTED)).thenReturn(6L);
        when(policyRepository.countByStatus(PolicyStatus.BOUND)).thenReturn(7L);
        when(policyRepository.countByStatus(PolicyStatus.ACTIVE)).thenReturn(8L);

        AgentFunnelResponse res = service.funnelForPrincipal("admin@example.com");

        assertEquals("ALL", res.getAgentEmail());
        assertEquals(26, res.getTotal());
        verify(policyRepository, never()).countByAssignedAgentEmailAndStatus(any(), any());
    }

    @Test
    void funnelForPrincipal_financeForbidden() {
        authenticateAs("finance@example.com", "ROLE_FINANCE");

        assertThrows(ForbiddenException.class, () -> service.funnelForPrincipal("finance@example.com"));
    }
}
