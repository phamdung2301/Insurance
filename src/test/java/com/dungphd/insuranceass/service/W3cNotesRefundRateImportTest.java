package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyNote;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.RateImport;
import com.dungphd.insuranceass.model.RefundRecord;
import com.dungphd.insuranceass.repository.PolicyNoteRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RateImportRepository;
import com.dungphd.insuranceass.repository.RefundRecordRepository;
import com.dungphd.insuranceass.service.impl.FinanceReconciliationServiceImpl;
import com.dungphd.insuranceass.service.impl.PolicyNoteServiceImpl;
import com.dungphd.insuranceass.service.impl.PricingExcelServiceImpl;
import com.dungphd.insuranceass.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Đợt 3C: notes UW, lịch sử import rates, refund + export đối soát.
 * PermissionService thật + SecurityContextHolder thật (mẫu đợt 2A) để
 * fail-closed được kiểm chứng thật, không mock permission.
 */
@ExtendWith(MockitoExtension.class)
class W3cNotesRefundRateImportTest {

    @Mock
    private PolicyNoteRepository policyNoteRepository;

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RateImportRepository rateImportRepository;

    private final PermissionService permissionService = new PermissionService();

    private PolicyNoteServiceImpl noteService;
    private RefundServiceImpl refundService;
    private FinanceReconciliationServiceImpl financeService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        noteService = new PolicyNoteServiceImpl(policyNoteRepository, permissionService);
        refundService = new RefundServiceImpl(refundRecordRepository);
        financeService = new FinanceReconciliationServiceImpl(refundRecordRepository, policyRepository, permissionService);
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

    // ---------- Việc 1: notes chỉ UW/ADMIN ----------

    @Test
    void notes_underwriterAndAdminCanReadAndWrite() {
        when(policyNoteRepository.findByPolicyNumberOrderByCreatedAtDesc("HD-1"))
                .thenReturn(List.of());
        when(policyNoteRepository.save(any(PolicyNote.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAs("uw@x.vn", "ROLE_UNDERWRITER");
        assertEquals(0, noteService.listNotes("HD-1").size());
        PolicyNote saved = noteService.addNote("HD-1", "  cần xem lại định phí  ");
        assertEquals("uw@x.vn", saved.getAuthorEmail());
        assertEquals("cần xem lại định phí", saved.getText());
        assertNotNull(saved.getCreatedAt());

        authenticateAs("admin@x.vn", "ROLE_ADMIN");
        noteService.listNotes("HD-1");
        noteService.addNote("HD-1", "ok");
    }

    @Test
    void notes_customerAgentFinanceAndAnonymousForbidden() {
        for (String[] creds : new String[][]{
                {"cus@x.vn", "ROLE_CUSTOMER"}, {"ag@x.vn", "ROLE_AGENT"}, {"fin@x.vn", "ROLE_FINANCE"}}) {
            authenticateAs(creds[0], creds[1]);
            assertThrows(ForbiddenException.class, () -> noteService.listNotes("HD-1"));
            assertThrows(ForbiddenException.class, () -> noteService.addNote("HD-1", "x"));
        }
        SecurityContextHolder.clearContext();
        assertThrows(ForbiddenException.class, () -> noteService.listNotes("HD-1"));
        assertThrows(ForbiddenException.class, () -> noteService.addNote("HD-1", "x"));
        verifyNoInteractions(policyNoteRepository);
    }

    // ---------- Việc 2: lịch sử import ----------

    private MockMultipartFile templateFile() {
        PricingExcelServiceImpl svc = new PricingExcelServiceImpl(policyRepository);
        byte[] bytes = svc.generatePricingTemplate();
        return new MockMultipartFile("file", "matrix.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    @Test
    void rateImport_savesHistoryWithPrincipalFileNameAndCount() {
        PricingExcelServiceImpl svc = new PricingExcelServiceImpl(policyRepository);
        ReflectionTestUtils.setField(svc, "rateImportRepository", rateImportRepository);
        ReflectionTestUtils.setField(svc, "permissionService", permissionService);
        when(rateImportRepository.save(any(RateImport.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAs("admin@x.vn", "ROLE_ADMIN");
        Map<String, Object> result = svc.importRatesFromExcel(templateFile());
        assertTrue((int) result.get("updatedCount") >= 6);

        ArgumentCaptor<RateImport> captor = ArgumentCaptor.forClass(RateImport.class);
        verify(rateImportRepository).save(captor.capture());
        RateImport history = captor.getValue();
        assertEquals("admin@x.vn", history.getImportedBy());
        assertEquals("matrix.xlsx", history.getFileName());
        assertEquals((int) result.get("updatedCount"), history.getRatesCount());
        assertNotNull(history.getImportedAt());
    }

    @Test
    void rateImport_mainImportStillSucceedsWhenHistorySaveFails() {
        PricingExcelServiceImpl svc = new PricingExcelServiceImpl(policyRepository);
        ReflectionTestUtils.setField(svc, "rateImportRepository", rateImportRepository);
        ReflectionTestUtils.setField(svc, "permissionService", permissionService);
        when(rateImportRepository.save(any(RateImport.class)))
                .thenThrow(new RuntimeException("Mongo down"));

        // Không principal → importedBy rơi về "System", nhưng save lỗi vẫn
        // phải bị nuốt và import chính trả kết quả bình thường.
        SecurityContextHolder.clearContext();
        Map<String, Object> result = svc.importRatesFromExcel(templateFile());
        assertTrue((int) result.get("updatedCount") >= 6);
    }

    @Test
    void rateImport_noPrincipalRecordsSystem() {
        PricingExcelServiceImpl svc = new PricingExcelServiceImpl(policyRepository);
        ReflectionTestUtils.setField(svc, "rateImportRepository", rateImportRepository);
        ReflectionTestUtils.setField(svc, "permissionService", permissionService);
        when(rateImportRepository.save(any(RateImport.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.clearContext();
        svc.importRatesFromExcel(templateFile());
        ArgumentCaptor<RateImport> captor = ArgumentCaptor.forClass(RateImport.class);
        verify(rateImportRepository).save(captor.capture());
        assertEquals("System", captor.getValue().getImportedBy());
    }

    // ---------- Việc 3: refund ----------

    @Test
    void refund_idempotentAndAmountEqualsTotalPremium() {
        Policy policy = Policy.builder().policyNumber("HD-9").totalPremium(1234567.0).build();
        when(refundRecordRepository.existsByPolicyNumber("HD-9")).thenReturn(false);
        when(refundRecordRepository.save(any(RefundRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        refundService.createForCancellation(policy);
        ArgumentCaptor<RefundRecord> captor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordRepository).save(captor.capture());
        assertEquals(1234567.0, captor.getValue().getAmount());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("cần đối soát thủ công", captor.getValue().getNote());
        assertNotNull(captor.getValue().getCreatedAt());

        // Lần 2: đã tồn tại → không save nữa.
        reset(refundRecordRepository);
        when(refundRecordRepository.existsByPolicyNumber("HD-9")).thenReturn(true);
        refundService.createForCancellation(policy);
        verify(refundRecordRepository, never()).save(any());
    }

    @Test
    void refund_neverThrows() {
        assertDoesNotThrow(() -> refundService.createForCancellation(null));
        assertDoesNotThrow(() -> refundService.createForCancellation(Policy.builder().build()));
        when(refundRecordRepository.existsByPolicyNumber(anyString())).thenThrow(new RuntimeException("db"));
        assertDoesNotThrow(() -> refundService.createForCancellation(
                Policy.builder().policyNumber("HD-9").totalPremium(1.0).build()));
    }

    // ---------- Việc 3: export CSV + finance-only ----------

    private Policy policy(String number, PolicyStatus status, String email,
                          Double premium, Instant claimedAt, Instant updatedAt) {
        Policy p = Policy.builder()
                .policyNumber(number).status(status).totalPremium(premium)
                .paymentClaimedAt(claimedAt).build();
        p.setInsured(Insured.builder().email(email).name("Khach").build());
        p.setUpdatedAt(updatedAt);
        return p;
    }

    @Test
    void exportCsv_containsBothGroupsAndFiltersByDate() {
        Instant inRangeClaim = Instant.parse("2026-10-15T10:00:00Z");
        Instant outRangeClaim = Instant.parse("2026-09-01T10:00:00Z");
        Instant inRangeConfirm = Instant.parse("2026-10-20T10:00:00Z");
        when(policyRepository.findByStatus(PolicyStatus.BOUND)).thenReturn(List.of(
                policy("HD-B1", PolicyStatus.BOUND, "a@x.vn", 1000.0, inRangeClaim, null),
                policy("HD-B2", PolicyStatus.BOUND, "b@x.vn", 2000.0, outRangeClaim, null),
                // BOUND nhưng chưa khai báo → không vào nhóm chờ.
                policy("HD-B3", PolicyStatus.BOUND, "c@x.vn", 3000.0, null, null)));
        when(policyRepository.findByStatus(PolicyStatus.ACTIVE)).thenReturn(List.of(
                policy("HD-A1", PolicyStatus.ACTIVE, "d@x.vn", 4000.0, null, inRangeConfirm)));

        authenticateAs("fin@x.vn", "ROLE_FINANCE");
        String csv = financeService.exportReconciliationCsv(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        assertTrue(csv.contains("policyNumber,insuredEmail,totalPremium,trangThaiDoiSoat,ngayKhaiBaoHoacXacNhan"));
        assertTrue(csv.contains("HD-B1") && csv.contains("CHO_DOI_SOAT") && csv.contains("2026-10-15"));
        assertTrue(csv.contains("HD-A1") && csv.contains("DA_XAC_NHAN") && csv.contains("2026-10-20"));
        assertFalse(csv.contains("HD-B2"), "ngoài khoảng ngày phải bị lọc");
        assertFalse(csv.contains("HD-B3"), "BOUND chưa khai báo không vào nhóm chờ");
    }

    @Test
    void finance_onlyFinanceAndAdmin() {
        authenticateAs("admin@x.vn", "ROLE_ADMIN");
        when(refundRecordRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        assertNotNull(financeService.listRefunds());
        when(policyRepository.findByStatus(any())).thenReturn(List.of());
        assertNotNull(financeService.exportReconciliationCsv(null, null));

        for (String[] creds : new String[][]{
                {"cus@x.vn", "ROLE_CUSTOMER"}, {"uw@x.vn", "ROLE_UNDERWRITER"}, {"ag@x.vn", "ROLE_AGENT"}}) {
            authenticateAs(creds[0], creds[1]);
            assertThrows(ForbiddenException.class, () -> financeService.listRefunds());
            assertThrows(ForbiddenException.class, () -> financeService.exportReconciliationCsv(null, null));
        }
        SecurityContextHolder.clearContext();
        assertThrows(ForbiddenException.class, () -> financeService.listRefunds());
    }
}
