package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.RefundRecord;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RefundRecordRepository;
import com.dungphd.insuranceass.service.impl.FinanceReconciliationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PR finance-scope: đối soát chỉ FINANCE + ADMIN (trước đây chỉ ADMIN).
 * Dùng PermissionService thật + SecurityContextHolder như PermissionServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class FinanceReconciliationServiceImplTest {

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private PolicyRepository policyRepository;

    private FinanceReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new FinanceReconciliationServiceImpl(
                refundRecordRepository, policyRepository, new PermissionService());
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

    @Test
    void finance_canListRefunds() {
        authenticateAs("finance@example.com", "ROLE_FINANCE");
        RefundRecord record = RefundRecord.builder().id("ref-1").build();
        when(refundRecordRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(record));

        List<RefundRecord> result = service.listRefunds();

        assertEquals(1, result.size());
        assertEquals("ref-1", result.get(0).getId());
    }

    @Test
    void admin_canListRefunds() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        when(refundRecordRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.listRefunds());
    }

    @Test
    void finance_canExportReconciliationCsv() {
        authenticateAs("finance@example.com", "ROLE_FINANCE");
        when(policyRepository.findByStatus(PolicyStatus.BOUND)).thenReturn(List.of());
        when(policyRepository.findByStatus(PolicyStatus.ACTIVE)).thenReturn(List.of());

        String csv = service.exportReconciliationCsv(null, null);

        assertTrue(csv.contains("policyNumber,insuredEmail,totalPremium,trangThaiDoiSoat,ngayKhaiBaoHoacXacNhan"));
    }

    @Test
    void otherRoles_cannotListRefundsOrExport() {
        authenticateAs("uw@example.com", "ROLE_UNDERWRITER");
        assertThrows(ForbiddenException.class, () -> service.listRefunds());
        assertThrows(ForbiddenException.class, () -> service.exportReconciliationCsv(null, null));

        authenticateAs("customer@example.com", "ROLE_CUSTOMER");
        assertThrows(ForbiddenException.class, () -> service.listRefunds());

        authenticateAs("agent@example.com", "ROLE_AGENT");
        assertThrows(ForbiddenException.class, () -> service.listRefunds());
    }

    @Test
    void noContext_cannotListRefunds() {
        assertThrows(ForbiddenException.class, () -> service.listRefunds());
    }
}
