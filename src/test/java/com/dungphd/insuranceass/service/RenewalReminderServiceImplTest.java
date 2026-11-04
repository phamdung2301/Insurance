package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.PolicyStatus;
import com.dungphd.insuranceass.model.RenewalReminder;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.RenewalReminderRepository;
import com.dungphd.insuranceass.service.impl.RenewalReminderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Dot 3B: renewal milestones 30/7/1 (theo ngay, mui gio Asia/Ho_Chi_Minh),
 * chong trung, failure-isolated theo policy.
 */
@ExtendWith(MockitoExtension.class)
class RenewalReminderServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RenewalReminderRepository renewalReminderRepository;

    @Mock
    private NotificationService notificationService;

    private RenewalReminderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RenewalReminderServiceImpl(
                policyRepository, renewalReminderRepository, notificationService);
    }

    private Policy policy(String number, long daysUntilExpiry) {
        Instant expiry = TODAY.plusDays(daysUntilExpiry).atStartOfDay(ZONE).toInstant();
        return Policy.builder()
                .policyNumber(number)
                .status(PolicyStatus.ACTIVE)
                .insured(Insured.builder().email("owner@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .expirationDate(expiry)
                .build();
    }

    private void stubCandidates(Policy... policies) {
        when(policyRepository.findByStatusAndExpirationDateBetween(
                eq(PolicyStatus.ACTIVE), any(), any())).thenReturn(List.of(policies));
    }

    @Test
    void milestones_30_7_1_sendNotificationAndSaveReminder() {
        Policy p30 = policy("POL-30", 30);
        Policy p7 = policy("POL-7", 7);
        Policy p1 = policy("POL-1", 1);
        stubCandidates(p30, p7, p1);
        when(renewalReminderRepository.existsByPolicyNumberAndDaysBefore(anyString(), anyInt()))
                .thenReturn(false);

        assertEquals(3, service.runRenewalReminders(TODAY));

        verify(notificationService).notifyRenewalReminder(p30, 30);
        verify(notificationService).notifyRenewalReminder(p7, 7);
        verify(notificationService).notifyRenewalReminder(p1, 1);
        ArgumentCaptor<RenewalReminder> captor = ArgumentCaptor.forClass(RenewalReminder.class);
        verify(renewalReminderRepository, times(3)).save(captor.capture());
        assertEquals(List.of(1, 7, 30),
                captor.getAllValues().stream().map(RenewalReminder::getDaysBefore).sorted().toList());
    }

    @Test
    void offMilestone_noSend() {
        // 15 ngay, 0 ngay (het han hom nay), 31 ngay, qua han: deu khong gui.
        stubCandidates(policy("POL-15", 15), policy("POL-0", 0),
                policy("POL-31", 31), policy("POL-PAST", -5));
        // Khong stub exists...: policy sai moc return truoc khi check reminder.

        assertEquals(0, service.runRenewalReminders(TODAY));
        verifyNoInteractions(notificationService);
        verify(renewalReminderRepository, never()).save(any());
    }

    @Test
    void existingReminder_noDuplicateSend() {
        Policy p30 = policy("POL-30", 30);
        stubCandidates(p30);
        when(renewalReminderRepository.existsByPolicyNumberAndDaysBefore("POL-30", 30))
                .thenReturn(true);

        assertEquals(0, service.runRenewalReminders(TODAY));
        verifyNoInteractions(notificationService);
        verify(renewalReminderRepository, never()).save(any());
    }

    @Test
    void onePolicyFails_othersStillProcessed() {
        Policy failing = policy("POL-BAD", 7);
        Policy good = policy("POL-GOOD", 7);
        stubCandidates(failing, good);
        when(renewalReminderRepository.existsByPolicyNumberAndDaysBefore(anyString(), anyInt()))
                .thenReturn(false);
        doThrow(new RuntimeException("smtp down")).when(notificationService)
                .notifyRenewalReminder(failing, 7);

        assertEquals(1, service.runRenewalReminders(TODAY));
        verify(notificationService).notifyRenewalReminder(good, 7);
        verify(renewalReminderRepository, times(1)).save(argThat(
                r -> "POL-GOOD".equals(r.getPolicyNumber()) && r.getDaysBefore() == 7));
    }

    @Test
    void nullExpiration_fallsBackToEffectivePlus365() {
        Policy legacy = Policy.builder()
                .policyNumber("POL-LEGACY")
                .status(PolicyStatus.ACTIVE)
                .insured(Insured.builder().email("owner@example.com").build())
                .effectiveDate(TODAY.minusDays(365 - 7).atStartOfDay(ZONE).toInstant())
                .expirationDate(null)
                .build();
        stubCandidates(legacy);
        when(renewalReminderRepository.existsByPolicyNumberAndDaysBefore(anyString(), anyInt()))
                .thenReturn(false);

        assertEquals(1, service.runRenewalReminders(TODAY));
        verify(notificationService).notifyRenewalReminder(legacy, 7);
    }
}
