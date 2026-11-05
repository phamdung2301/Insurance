package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.TransactionLedgerServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLedgerServiceImplTest {

    @Mock
    private PolicyTransactionRepository transactionRepository;

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private TransactionLedgerServiceImpl transactionLedgerService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    private void injectMoneyReadGuards(Policy policy) {
        ReflectionTestUtils.setField(transactionLedgerService, "policyRepository", policyRepository);
        ReflectionTestUtils.setField(transactionLedgerService, "permissionService", new PermissionService());
        when(policyRepository.findByPolicyNumber("POL-100")).thenReturn(Optional.of(policy));
    }

    private Policy ownedPolicy() {
        return Policy.builder()
                .policyNumber("POL-100")
                .insured(Insured.builder().email("owner@example.com").build())
                .build();
    }

    @Test
    void recordTransaction_Success() {
        PolicyTransaction mockTxn = PolicyTransaction.builder()
                .id("tx-1")
                .policyNumber("POL-100")
                .version(1)
                .transactionType(TransactionType.CREATE_POLICY)
                .actor("admin@gmail.com")
                .description("Created policy")
                .timestamp(Instant.now())
                .build();

        when(transactionRepository.save(any(PolicyTransaction.class))).thenReturn(mockTxn);

        PolicyTransaction saved = transactionLedgerService.recordTransaction(
                "POL-100", 1, TransactionType.CREATE_POLICY, "admin@gmail.com", "Created policy"
        );

        assertNotNull(saved);
        assertEquals("POL-100", saved.getPolicyNumber());
        assertEquals(TransactionType.CREATE_POLICY, saved.getTransactionType());
        verify(transactionRepository).save(any(PolicyTransaction.class));
    }

    @Test
    void recordTransaction_NullPolicyNumber_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                transactionLedgerService.recordTransaction(null, 1, TransactionType.CREATE_POLICY, "System", "Test")
        );
    }

    @Test
    void getTransactions_Success() {
        PolicyTransaction tx1 = PolicyTransaction.builder()
                .id("tx-1")
                .policyNumber("POL-100")
                .version(2)
                .transactionType(TransactionType.ENDORSEMENT)
                .timestamp(Instant.now())
                .build();

        when(transactionRepository.findByPolicyNumberOrderByTimestampDesc("POL-100")).thenReturn(List.of(tx1));

        List<PolicyTransaction> history = transactionLedgerService.getTransactions("POL-100");

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(TransactionType.ENDORSEMENT, history.get(0).getTransactionType());
    }

    // FINANCE (PR finance-scope): ledger là dấu vết tiền nên FINANCE được đọc;
    // người lạ (không phải bên cầu) vẫn 403.
    @Test
    void getTransactions_financeAllowed() {
        authenticateAs("finance@example.com", "ROLE_FINANCE");
        injectMoneyReadGuards(ownedPolicy());
        PolicyTransaction tx1 = PolicyTransaction.builder()
                .id("tx-1")
                .policyNumber("POL-100")
                .version(2)
                .transactionType(TransactionType.CREATE_POLICY)
                .timestamp(Instant.now())
                .build();
        when(transactionRepository.findByPolicyNumberOrderByTimestampDesc("POL-100")).thenReturn(List.of(tx1));

        List<PolicyTransaction> history = transactionLedgerService.getTransactions("POL-100");

        assertEquals(1, history.size());
        assertEquals(TransactionType.CREATE_POLICY, history.get(0).getTransactionType());
    }

    @Test
    void getTransactions_strangerCustomer_throws403() {
        authenticateAs("stranger@example.com", "ROLE_CUSTOMER");
        injectMoneyReadGuards(ownedPolicy());

        assertThrows(ForbiddenException.class,
                () -> transactionLedgerService.getTransactions("POL-100"));
    }
}
