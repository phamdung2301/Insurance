package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyVersionRepository;
import com.dungphd.insuranceass.service.impl.DocumentGenerationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

/**
 * Dot 3B: invoice qua checkPolicyRead o muc service (pattern PermissionServiceTest:
 * tu set SecurityContextHolder). DocumentGenerationServiceImpl nhan
 * PermissionService qua @Autowired(required=false) field injection nen test
 * set bean that bang ReflectionTestUtils.
 */
@ExtendWith(MockitoExtension.class)
class DocumentGenerationServiceImplInvoiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyVersionRepository policyVersionRepository;

    private DocumentGenerationServiceImpl service;
    private Policy policy;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new DocumentGenerationServiceImpl(policyRepository, policyVersionRepository);
        ReflectionTestUtils.setField(service, "permissionService", new PermissionService());

        policy = Policy.builder()
                .policyNumber("POL-INV-1")
                .status(PolicyStatus.ACTIVE)
                .insured(Insured.builder().name("Owner Corp").email("owner@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .totalPremium(1000000.0)
                .paymentStatus(PaymentStatus.CLAIMED)
                .paymentClaimedAt(Instant.now())
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now())
                .locations(List.of(Location.builder().locationId(1).address("123 ABC")
                        .coverages(List.of(Coverage.builder().coverageCode("GL")
                                .coverageName("General Liability").coverageType("STANDARD")
                                .limit(100000.0).deductible(1000.0).premium(1000000.0).build()))
                        .build()))
                .build();
        when(policyRepository.findByPolicyNumber("POL-INV-1")).thenReturn(Optional.of(policy));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, authorities));
    }

    @Test
    void owner_canDownloadInvoice_returnsPdfBytes() {
        authenticateAs("owner@example.com", "ROLE_CUSTOMER");
        byte[] pdf = service.generateInvoicePdf("POL-INV-1");
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void otherCustomer_cannotDownloadInvoice_throwsForbidden() {
        authenticateAs("other@example.com", "ROLE_CUSTOMER");
        assertThrows(ForbiddenException.class, () -> service.generateInvoicePdf("POL-INV-1"));
    }

    @Test
    void noContext_cannotDownloadInvoice_throwsForbidden() {
        assertThrows(ForbiddenException.class, () -> service.generateInvoicePdf("POL-INV-1"));
    }

    @Test
    void admin_canDownloadInvoice() {
        authenticateAs("admin@example.com", "ROLE_ADMIN");
        assertNotNull(service.generateInvoicePdf("POL-INV-1"));
    }
}
