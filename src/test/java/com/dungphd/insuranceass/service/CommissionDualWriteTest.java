package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Commission;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.CommissionRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.service.impl.CommissionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1: CommissionServiceImpl.createForBound copy Policy.agentId sang
 * Commission.agentId, giữ nguyên agentEmail + logic skip cũ.
 */
@ExtendWith(MockitoExtension.class)
class CommissionDualWriteTest {

    @Mock
    private CommissionRepository commissionRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private CommissionServiceImpl commissionService;

    @Test
    void createForBound_copiesAgentId() {
        when(commissionRepository.existsByPolicyNumber("POL-C-1")).thenReturn(false);
        when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

        commissionService.createForBound(Policy.builder()
                .policyNumber("POL-C-1")
                .assignedAgentEmail("agent@example.com")
                .agentId("tenant-1")
                .totalPremium(1_000_000.0)
                .build());

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        assertEquals("tenant-1", captor.getValue().getAgentId());
        assertEquals("agent@example.com", captor.getValue().getAgentEmail());
    }

    @Test
    void createForBound_legacyPolicyWithoutAgentId_stillCreatesWithNullAgentId() {
        when(commissionRepository.existsByPolicyNumber("POL-C-2")).thenReturn(false);
        when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

        commissionService.createForBound(Policy.builder()
                .policyNumber("POL-C-2")
                .assignedAgentEmail("agent@example.com")
                .agentId(null)
                .totalPremium(500_000.0)
                .build());

        ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
        verify(commissionRepository).save(captor.capture());
        assertNull(captor.getValue().getAgentId(), "policy tiền-Phase 1 chưa có agentId: commission tạo với null, migration backfill sau");
        assertEquals("agent@example.com", captor.getValue().getAgentEmail());
    }

    @Test
    void createForBound_noAgentEmail_stillSkipped() {
        commissionService.createForBound(Policy.builder()
                .policyNumber("POL-C-3")
                .assignedAgentEmail(null)
                .agentId("tenant-1")
                .build());
        verify(commissionRepository, never()).save(any(Commission.class));
    }
}
