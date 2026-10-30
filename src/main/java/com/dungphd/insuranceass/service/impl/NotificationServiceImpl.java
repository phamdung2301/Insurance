package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.NotificationRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.MailService;
import com.dungphd.insuranceass.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Override
    public void notifyNewOffer(Policy policy, NegotiationRole offerRole, String actorEmail) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            String link = "/hop-dong/" + policy.getPolicyNumber();
            if (offerRole == NegotiationRole.UNDERWRITER) {
                notifyMany(policyRecipients(policy, actorEmail),
                        Notification.TYPE_NEW_OFFER,
                        "Underwriter đã phản hồi giá",
                        "Hợp đồng " + policy.getPolicyNumber() + " vừa có phản hồi giá mới từ underwriter.",
                        link);
            } else {
                // Phe cầu vừa ra offer → tới lượt underwriter: gửi cho TẤT CẢ UW.
                // UserRepository không có query theo role nên dùng findAll + lọc
                // (ghi rõ theo brief; số lượng user nội bộ nhỏ, chấp nhận được).
                List<String> underwriters = new ArrayList<>();
                try {
                    for (User user : userRepository.findAll()) {
                        if (user != null && user.getRoles() != null
                                && user.getRoles().contains(Role.ROLE_UNDERWRITER.name())
                                && user.getEmail() != null && !user.getEmail().isBlank()
                                && !emailEquals(user.getEmail(), actorEmail)) {
                            underwriters.add(user.getEmail());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to resolve underwriter recipients for policy {}: {}",
                            policy.getPolicyNumber(), e.getMessage());
                    return;
                }
                notifyMany(underwriters,
                        Notification.TYPE_NEW_OFFER,
                        "Có đề xuất giá mới",
                        "Hợp đồng " + policy.getPolicyNumber() + " vừa có đề xuất giá mới, chờ underwriter phản hồi.",
                        link);
            }
        } catch (Exception e) {
            // Failure-isolated: offer đã save thành công, notification không được phá nó.
            log.warn("Failed to create NEW_OFFER notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public void notifyPolicyActivated(Policy policy) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            notifyMany(policyRecipients(policy, null),
                    Notification.TYPE_POLICY_ACTIVATED,
                    "Hợp đồng đã kích hoạt",
                    "Hợp đồng " + policy.getPolicyNumber() + " đã chuyển sang trạng thái ACTIVE và có hiệu lực.",
                    "/hop-dong/" + policy.getPolicyNumber());
        } catch (Exception e) {
            log.warn("Failed to create POLICY_ACTIVATED notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public void notifyEndorsementApplied(Policy policy) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            notifyMany(policyRecipients(policy, null),
                    Notification.TYPE_ENDORSEMENT_APPLIED,
                    "Hợp đồng đã được điều chỉnh",
                    "Hợp đồng " + policy.getPolicyNumber() + " vừa được áp dụng endorsement, phiên bản mới đã có hiệu lực.",
                    "/hop-dong/" + policy.getPolicyNumber());
        } catch (Exception e) {
            log.warn("Failed to create ENDORSEMENT_APPLIED notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    /**
     * Gửi cho từng recipient, mỗi recipient try/catch riêng: một recipient lỗi
     * không chặn các recipient khác, và không bao giờ throw ra caller.
     * Email chỉ best-effort (MailService tự catch SMTP + null sender; ở đây
     * vẫn bọc thêm try/catch).
     */
    private void notifyMany(List<String> recipients, String type, String title, String message, String link) {
        if (recipients == null) {
            return;
        }
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            try {
                notificationRepository.save(Notification.builder()
                        .recipientEmail(recipient)
                        .type(type)
                        .title(title)
                        .message(message)
                        .link(link)
                        .read(false)
                        .createdAt(Instant.now())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to save notification for {}: {}", recipient, e.getMessage());
                continue;
            }
            try {
                if (mailService != null) {
                    mailService.sendNotificationEmail(recipient, title, message, link);
                }
            } catch (Exception e) {
                log.warn("Failed to send notification email to {}: {}", recipient, e.getMessage());
            }
        }
    }

    /** Chủ policy + assignedAgentEmail, loại trùng (không phân biệt hoa/thường), loại trừ actor. */
    private List<String> policyRecipients(Policy policy, String actorEmail) {
        Set<String> seenLower = new LinkedHashSet<>();
        List<String> recipients = new ArrayList<>();
        String insuredEmail = policy.getInsured() != null ? policy.getInsured().getEmail() : null;
        addRecipient(recipients, seenLower, insuredEmail, actorEmail);
        addRecipient(recipients, seenLower, policy.getAssignedAgentEmail(), actorEmail);
        return recipients;
    }

    private void addRecipient(List<String> recipients, Set<String> seenLower, String email, String actorEmail) {
        if (email == null || email.isBlank() || emailEquals(email, actorEmail)) {
            return;
        }
        if (seenLower.add(email.trim().toLowerCase())) {
            recipients.add(email.trim());
        }
    }

    @Override
    public void notifyRenewalReminder(Policy policy, int daysBefore) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            notifyMany(policyRecipients(policy, null),
                    Notification.TYPE_RENEWAL_REMINDER,
                    "Hop dong sap het han",
                    "Hop dong " + policy.getPolicyNumber() + " sap het han trong "
                            + daysBefore + " ngay. Vui long lien he de tai tuc truoc khi het han.",
                    "/hop-dong/" + policy.getPolicyNumber());
        } catch (Exception e) {
            // Failure-isolated: loi notification khong duoc pha luong quet tai tuc.
            log.warn("Failed to create RENEWAL_REMINDER notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public List<Notification> listForPrincipal(String principalEmail, boolean unreadOnly) {
        String principal = requirePrincipal(principalEmail);
        if (unreadOnly) {
            return notificationRepository.findByRecipientEmailAndReadOrderByCreatedAtDesc(principal, false);
        }
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(principal);
    }

    @Override
    public long unreadCount(String principalEmail) {
        String principal = requirePrincipal(principalEmail);
        return notificationRepository.countByRecipientEmailAndRead(principal, false);
    }

    @Override
    public Notification markAsRead(String principalEmail, String id) {
        String principal = requirePrincipal(principalEmail);
        // findByIdAndRecipientEmail: sai chủ hoặc không tồn tại đều → 404, không lộ.
        Notification notification = notificationRepository.findByIdAndRecipientEmail(id, principal)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    /** Fail-closed: không có principal hợp lệ thì throw, không trả dữ liệu của ai cả. */
    private String requirePrincipal(String principalEmail) {
        if (principalEmail == null || principalEmail.isBlank()
                || "anonymousUser".equalsIgnoreCase(principalEmail.trim())) {
            throw new ForbiddenException("Yêu cầu đăng nhập để xem thông báo.");
        }
        return principalEmail.trim();
    }

    private static boolean emailEquals(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
