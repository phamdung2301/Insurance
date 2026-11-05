package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.context.TenantContext;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SaaS Phase 2 Track B mục 3 — kiểm chứng ở tầng query Mongo thật
 * (không chỉ assert criteria): /mine vs /policies với agent thuần tạo hộ
 * và tài khoản dual-role AGENT+CUSTOMER.
 *
 * <p>PolicyServiceImpl dùng PermissionService thật (gắn AgentRepository
 * mock ACTIVE cho tenant-1) + MongoTemplate mock bắt Query, nên test này
 * chứng minh đúng query sẽ chạy trên DB, không chỉ logic set field.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PolicySearchScopeQueryTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PremiumCalculationService premiumCalculationService;

    @Mock
    private PolicyTransactionRepository policyTransactionRepository;

    @Mock
    private TransactionLedgerService transactionLedgerService;

    @Mock
    private AgentRepository agentRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    private PolicyServiceImpl service() {
        PolicyServiceImpl impl = new PolicyServiceImpl(
                policyRepository, mongoTemplate, premiumCalculationService,
                policyTransactionRepository, transactionLedgerService);
        PermissionService permissionService = new PermissionService(agentRepository);
        when(agentRepository.findById("tenant-1")).thenReturn(Optional.of(
                Agent.builder().id("tenant-1").status(AgentStatus.ACTIVE).build()));
        when(agentRepository.findById("tenant-closed")).thenReturn(Optional.of(
                Agent.builder().id("tenant-closed").status(AgentStatus.CLOSED).build()));
        ReflectionTestUtils.setField(impl, "permissionService", permissionService);
        when(mongoTemplate.count(any(Query.class), eq(Policy.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Policy.class)))
                .thenReturn(Collections.emptyList());
        return impl;
    }

    private String capturedQueryJson(PolicyServiceImpl impl, PolicySearchCriteria criteria, boolean mine, String mineEmail) {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(captor.capture(), eq(Policy.class)))
                .thenReturn(Collections.emptyList());
        if (mine) {
            impl.searchMyPolicies(criteria, mineEmail);
        } else {
            impl.searchPolicies(criteria);
        }
        return captor.getValue().getQueryObject().toJson();
    }

    @Test
    void pureAgent_policies_queryFiltersByTenantId_notEmail() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        String json = capturedQueryJson(service(), PolicySearchCriteria.builder().build(), false, null);
        assertTrue(json.contains("agentId"), json);
        assertTrue(json.contains("tenant-1"), json);
        assertFalse(json.contains("assignedAgentEmail"), json);
    }

    @Test
    void dualRole_policies_queryIsSingleOr_insuredOrTenant() {
        authenticateAs("agent@example.com", "ROLE_AGENT", "ROLE_CUSTOMER");
        TenantContext.setTenantId("tenant-1");
        String json = capturedQueryJson(service(), PolicySearchCriteria.builder().build(), false, null);
        assertTrue(json.contains("$or"), json);
        assertTrue(json.contains("agent@example.com"), json);
        assertTrue(json.contains("tenant-1"), json);
    }

    @Test
    void pureAgent_mine_queryContainsOnlyInsuredEmail() {
        // Agent thuần tạo hộ khách: /mine của agent chỉ lọc insured =
        // email agent — policy tạo hộ (insured là khách, agentId là tenant)
        // KHÔNG khớp query này, đúng định nghĩa /mine mới.
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-1");
        String json = capturedQueryJson(service(), PolicySearchCriteria.builder().build(), true, "agent@example.com");
        assertTrue(json.contains("insured"), json);
        assertTrue(json.contains("agent@example.com"), json);
        assertFalse(json.contains("agentId"), json);
    }

    @Test
    void customer_mine_queryContainsOnlyOwnEmail() {
        authenticateAs("customer@example.com", "ROLE_CUSTOMER");
        String json = capturedQueryJson(service(), PolicySearchCriteria.builder().build(), true, "customer@example.com");
        assertTrue(json.contains("customer@example.com"), json);
        assertFalse(json.contains("agentId"), json);
        assertFalse(json.contains("$or"), json);
    }

    @Test
    void pureAgent_withoutTenant_policies_throwsFailClosed() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service().searchPolicies(PolicySearchCriteria.builder().build()));
        assertTrue(ex.getMessage().contains("chưa được gán tenant"), ex.getMessage());
    }

    @Test
    void pureAgent_closedTenant_policies_throwsClosedRead() {
        authenticateAs("agent@example.com", "ROLE_AGENT");
        TenantContext.setTenantId("tenant-closed");
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service().searchPolicies(PolicySearchCriteria.builder().build()));
        assertTrue(ex.getMessage().contains("CLOSED"), ex.getMessage());
    }
}
