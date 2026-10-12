package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.PolicyTransaction;
import com.dungphd.insuranceass.model.TransactionType;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.service.impl.TransactionLedgerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionLedgerServiceImplTest {

    @Mock
    private PolicyTransactionRepository transactionRepository;

    @InjectMocks
    private TransactionLedgerServiceImpl transactionLedgerService;

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
}
