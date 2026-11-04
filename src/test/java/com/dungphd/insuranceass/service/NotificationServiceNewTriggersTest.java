package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.model.Claim;
import com.dungphd.insuranceass.model.Insured;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.model.Payment;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceNewTriggersTest {

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
                .totalPremium(5_000_000.0)
                .effectiveDate(Instant.now())
                .expirationDate(Instant.now().plusSeconds(86400L * 365))
                .build();
    }

    @Test
    void notifyPaymentSucceeded_sendsToInsuredAndAgentWithMail() {
        Payment payment = Payment.builder()
                .policyNumber("POL-001").amount(5_000_000.0).txnRef("POL001-1-ABCDEF").build();
        notificationService.notifyPaymentSucceeded(policy, payment);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> Notification.TYPE_PAYMENT_SUCCEEDED.equals(n.getType())
                        && "/hop-dong/POL-001".equals(n.getLink())));
        List<String> recipients = captor.getAllValues().stream().map(Notification::getRecipientEmail).toList();
        assertTrue(recipients.contains("customer@example.com"));
        assertTrue(recipients.contains("agent@example.com"));
        assertTrue(captor.getAllValues().get(0).getMessage().contains("POL001-1-ABCDEF"));
        verify(mailService, times(2)).sendNotificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyEndorsementRequested_pending_sendsToDemandAndAllUnderwriters() {
        User uw1 = User.builder().email("uw1@example.com").roles(List.of(Role.ROLE_UNDERWRITER.name())).build();
        User uw2 = User.builder().email("uw2@example.com").roles(List.of(Role.ROLE_UNDERWRITER.name())).build();
        User customer = User.builder().email("customer@example.com").roles(List.of(Role.ROLE_CUSTOMER.name())).build();
        when(userRepository.findAll()).thenReturn(List.of(uw1, uw2, customer));

        EndorsementRequest request = EndorsementRequest.builder()
                .reason("Mở rộng xưởng").description("Thêm nhà kho").build();
        notificationService.notifyEndorsementRequested(policy, request, "customer@example.com",
                "pending-1", 1.3333, 750.0, 1750.0, false);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> Notification.TYPE_ENDORSEMENT_REQUESTED.equals(n.getType())));
        List<String> recipients = captor.getAllValues().stream().map(Notification::getRecipientEmail).toList();
        assertTrue(recipients.containsAll(List.of("customer@example.com", "agent@example.com",
                "uw1@example.com", "uw2@example.com")));
        assertTrue(captor.getAllValues().get(0).getMessage().contains("đang chờ thẩm định viên duyệt"));
        assertTrue(captor.getAllValues().get(0).getMessage().contains("pending-1"));
        verify(mailService, times(4)).sendNotificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyEndorsementRequested_directApply_marksAppliedImmediately() {
        when(userRepository.findAll()).thenReturn(List.of());
        EndorsementRequest request = EndorsementRequest.builder().reason("Đổi địa chỉ").build();
        notificationService.notifyEndorsementRequested(policy, request, "customer@example.com",
                null, null, null, null, true);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().get(0).getMessage().contains("đã được áp dụng ngay"));
    }

    @Test
    void notifyClaimApproved_withPolicy_sendsToInsuredAndAgent() {
        Claim claim = Claim.builder().id("claim-1").policyNumber("POL-001")
                .claimantEmail("customer@example.com")
                .claimedAmount(2_000_000.0).approvedAmount(1_500_000.0)
                .handlerNote("Đủ hồ sơ").build();
        notificationService.notifyClaimApproved(claim, policy);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(n -> Notification.TYPE_CLAIM_APPROVED.equals(n.getType())
                        && "/claims/claim-1".equals(n.getLink())));
        assertTrue(captor.getAllValues().get(0).getMessage().contains("đang chờ chi trả"));
        verify(mailService, times(2)).sendNotificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyClaimApproved_policyNull_fallsBackToClaimantOnly() {
        Claim claim = Claim.builder().id("claim-1").policyNumber("POL-001")
                .claimantEmail("customer@example.com")
                .claimedAmount(2_000_000.0).approvedAmount(1_500_000.0).build();
        notificationService.notifyClaimApproved(claim, null);

        verify(notificationRepository, times(1)).save(argThat(n ->
                "customer@example.com".equals(n.getRecipientEmail())
                        && Notification.TYPE_CLAIM_APPROVED.equals(n.getType())));
        verify(mailService, times(1)).sendNotificationEmail(anyString(), anyString(), anyString(), anyString());
    }
}
