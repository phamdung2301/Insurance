package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.NotificationRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Policy policy;

    @BeforeEach
    void setUp() {
        policy = Policy.builder()
                .policyNumber("POL-001")
                .insured(Insured.builder().email("customer@example.com").build())
                .assignedAgentEmail("agent@example.com")
                .build();
    }

    @Test
    void notifyPolicyActivated_sendsToInsuredAndAgent() {
        notificationService.notifyPolicyActivated(policy);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<String> recipients = captor.getAllValues().stream()
                .map(Notification::getRecipientEmail).toList();
        assertTrue(recipients.contains("customer@example.com"));
        assertTrue(recipients.contains("agent@example.com"));
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> Notification.TYPE_POLICY_ACTIVATED.equals(n.getType())
                        && "/hop-dong/POL-001".equals(n.getLink())
                        && !n.isRead()));
    }

    @Test
    void notifyPolicyActivated_dedupesInsuredEqualsAgentAndExcludesBlanks() {
        policy.setAssignedAgentEmail("CUSTOMER@example.com"); // trùng insured, khác case
        notificationService.notifyPolicyActivated(policy);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void notifyNewOffer_fromDemandSide_sendsToAllUnderwritersOnly() {
        User uw1 = User.builder().email("uw1@example.com").roles(List.of(Role.ROLE_UNDERWRITER.name())).build();
        User uw2 = User.builder().email("uw2@example.com").roles(List.of(Role.ROLE_UNDERWRITER.name())).build();
        User customer = User.builder().email("customer@example.com").roles(List.of(Role.ROLE_CUSTOMER.name())).build();
        when(userRepository.findAll()).thenReturn(List.of(uw1, uw2, customer));

        notificationService.notifyNewOffer(policy, NegotiationRole.CUSTOMER, "customer@example.com");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<String> recipients = captor.getAllValues().stream()
                .map(Notification::getRecipientEmail).toList();
        assertTrue(recipients.contains("uw1@example.com"));
        assertTrue(recipients.contains("uw2@example.com"));
        assertFalse(recipients.contains("customer@example.com"));
        assertEquals(Notification.TYPE_NEW_OFFER, captor.getAllValues().get(0).getType());
    }

    @Test
    void notifyNewOffer_fromUnderwriter_sendsToInsuredAndAgentExcludingActor() {
        notificationService.notifyNewOffer(policy, NegotiationRole.UNDERWRITER, "agent@example.com");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertEquals("customer@example.com", captor.getValue().getRecipientEmail());
        verify(userRepository, never()).findAll();
    }

    @Test
    void list_scopedToPrincipal_unreadOnlyUsesReadFilter() {
        Notification own = Notification.builder().recipientEmail("a@example.com").build();
        when(notificationRepository.findByRecipientEmailAndReadOrderByCreatedAtDesc("a@example.com", false))
                .thenReturn(List.of(own));

        List<Notification> result = notificationService.listForPrincipal("a@example.com", true);

        assertEquals(List.of(own), result);
        verify(notificationRepository, never()).findByRecipientEmailOrderByCreatedAtDesc(any());
    }

    @Test
    void list_all_scopedToPrincipal() {
        when(notificationRepository.findByRecipientEmailOrderByCreatedAtDesc("a@example.com"))
                .thenReturn(List.of());
        notificationService.listForPrincipal("a@example.com", false);
        verify(notificationRepository).findByRecipientEmailOrderByCreatedAtDesc("a@example.com");
    }

    @Test
    void unreadCount_scopedToPrincipal() {
        when(notificationRepository.countByRecipientEmailAndRead("a@example.com", false)).thenReturn(3L);
        assertEquals(3L, notificationService.unreadCount("a@example.com"));
    }

    @Test
    void list_withoutPrincipal_throwsFailClosed() {
        assertThrows(ForbiddenException.class, () -> notificationService.listForPrincipal(null, false));
        assertThrows(ForbiddenException.class, () -> notificationService.unreadCount("  "));
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markAsRead_wrongOwner_returns404() {
        when(notificationRepository.findByIdAndRecipientEmail("n1", "a@example.com"))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead("a@example.com", "n1"));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_own_setsReadTrue() {
        Notification own = Notification.builder().id("n1").recipientEmail("a@example.com").read(false).build();
        when(notificationRepository.findByIdAndRecipientEmail("n1", "a@example.com"))
                .thenReturn(Optional.of(own));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.markAsRead("a@example.com", "n1");
        assertTrue(result.isRead());
    }

    @Test
    void failureIsolation_repositoryThrows_mainOperationUnaffected() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("Mongo down"));
        // Không throw: thao tác chính (đã save policy) không bị ảnh hưởng.
        assertDoesNotThrow(() -> notificationService.notifyPolicyActivated(policy));
        assertDoesNotThrow(() -> notificationService.notifyEndorsementApplied(policy));
    }

    @Test
    void failureIsolation_emailThrows_notificationStillSaved() {
        doThrow(new RuntimeException("SMTP down"))
                .when(mailService).sendNotificationEmail(any(), any(), any(), any());
        assertDoesNotThrow(() -> notificationService.notifyPolicyActivated(policy));
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }
}
