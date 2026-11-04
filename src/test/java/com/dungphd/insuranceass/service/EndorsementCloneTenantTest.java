package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.EndorsementServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SaaS Phase 1: clonePolicy (snapshot endorsement) phải giữ nguyên agentId
 * như giữ assignedAgentEmail — nếu không, endorsement sẽ lặng lẽ làm mất
 * tenant của policy. Gọi private method qua ReflectionTestUtils vì clone chỉ
 * là bước nội bộ của applyEndorsement.
 */
@ExtendWith(MockitoExtension.class)
class EndorsementCloneTenantTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @InjectMocks
    private EndorsementServiceImpl endorsementService;

    @Test
    void clonePolicy_preservesAgentId() {
        Policy origin = Policy.builder()
                .id("p1")
                .policyNumber("POL-E-1")
                .agentId("tenant-1")
                .assignedAgentEmail("agent@example.com")
                .build();

        Policy cloned = ReflectionTestUtils.invokeMethod(endorsementService, "clonePolicy", origin);

        assertEquals("tenant-1", cloned.getAgentId());
        assertEquals("agent@example.com", cloned.getAssignedAgentEmail());
        assertEquals("POL-E-1", cloned.getPolicyNumber());
    }

    @Test
    void clonePolicy_withoutAgent_staysNull() {
        Policy origin = Policy.builder()
                .policyNumber("POL-E-2")
                .agentId(null)
                .assignedAgentEmail(null)
                .build();

        Policy cloned = ReflectionTestUtils.invokeMethod(endorsementService, "clonePolicy", origin);

        assertEquals(null, cloned.getAgentId());
        assertEquals(null, cloned.getAssignedAgentEmail());
    }
}
