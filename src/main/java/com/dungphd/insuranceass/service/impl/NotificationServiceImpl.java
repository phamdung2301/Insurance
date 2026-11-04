package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Claim;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.model.Payment;
import com.dungphd.insuranceass.model.Policy;
import com.dungphd.insuranceass.model.Role;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.NotificationRepository;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.MailService;
import com.dungphd.insuranceass.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Link tuyệt đối trong mail = FRONTEND_URL + link tương đối. @Value để
     * default rỗng (khác với application.properties vốn default
     * http://localhost:5173 khi chạy app thật): khi field rỗng/null (vd unit
     * test không chạy Spring, hoặc env chưa set ở tầng khác) thì mail chỉ ghi
     * số hợp đồng, không hardcode domain prod, không đoán domain.
     */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

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

    @Override
    public void notifyPaymentSucceeded(Policy policy, Payment payment) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            String link = "/hop-dong/" + policy.getPolicyNumber();
            StringBuilder msg = new StringBuilder("Hợp đồng " + policy.getPolicyNumber()
                    + " đã thanh toán thành công qua cổng VNPay và chuyển sang trạng thái ACTIVE.");
            if (payment != null) {
                if (payment.getAmount() != null) {
                    msg.append(" Số tiền đã trả: ").append(formatVnd(payment.getAmount())).append(" VND.");
                }
                if (payment.getTxnRef() != null) {
                    msg.append(" Mã giao dịch: ").append(payment.getTxnRef()).append(".");
                }
            }
            if (policy.getEffectiveDate() != null) {
                msg.append(" Ngày hiệu lực: ").append(policy.getEffectiveDate()).append(".");
            }
            if (policy.getExpirationDate() != null) {
                msg.append(" Ngày hết hạn: ").append(policy.getExpirationDate()).append(".");
            }
            msg.append(" Quý khách dùng số hợp đồng ").append(policy.getPolicyNumber()).append(" để tra cứu.");
            notifyMany(policyRecipients(policy, null),
                    Notification.TYPE_PAYMENT_SUCCEEDED,
                    "Thanh toán thành công — hợp đồng đã kích hoạt",
                    msg.toString(),
                    link,
                    absoluteMailLink(link, policy.getPolicyNumber()));
        } catch (Exception e) {
            log.warn("Failed to create PAYMENT_SUCCEEDED notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public void notifyEndorsementRequested(Policy policy, EndorsementRequest request, String actorEmail,
                                           String pendingId, Double premiumChangePct,
                                           Double oldPremium, Double projectedPremium,
                                           boolean appliedImmediately) {
        try {
            if (policy == null || policy.getPolicyNumber() == null) {
                return;
            }
            String link = "/hop-dong/" + policy.getPolicyNumber();
            StringBuilder msg = new StringBuilder("Hợp đồng " + policy.getPolicyNumber()
                    + " vừa có yêu cầu điều chỉnh mới");
            if (actorEmail != null && !actorEmail.isBlank()) {
                msg.append(" từ ").append(actorEmail);
            }
            msg.append(".");
            if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
                msg.append(" Lý do: ").append(request.getReason()).append(".");
                if (request.getDescription() != null && !request.getDescription().isBlank()) {
                    msg.append(" ").append(request.getDescription());
                }
            }
            if (appliedImmediately) {
                msg.append(" Yêu cầu đã được áp dụng ngay.");
            } else {
                msg.append(" Yêu cầu đang chờ thẩm định viên duyệt.");
                if (pendingId != null) {
                    msg.append(" Mã chờ duyệt: ").append(pendingId).append(".");
                }
                if (premiumChangePct != null) {
                    msg.append(" Mức thay đổi phí dự kiến: ")
                            .append(String.format("%.1f%%", premiumChangePct * 100));
                    if (oldPremium != null && projectedPremium != null) {
                        msg.append(" (").append(formatVnd(oldPremium)).append(" → ")
                                .append(formatVnd(projectedPremium)).append(" VND)");
                    }
                    msg.append(".");
                }
            }
            // Người nhận = bên cầu (xác nhận đã nhận yêu cầu) + tất cả UW
            // (có việc cần xử lý), hợp nhất loại trùng, loại trừ actor ở phía UW
            // (policyRecipients đã loại actor; phía UW lọc ở dưới).
            List<String> recipients = new java.util.ArrayList<>(policyRecipients(policy, null));
            java.util.Set<String> seenLower = new java.util.LinkedHashSet<>();
            for (String r : recipients) {
                seenLower.add(r.trim().toLowerCase());
            }
            for (String uw : underwriterRecipients(actorEmail)) {
                if (seenLower.add(uw.trim().toLowerCase())) {
                    recipients.add(uw);
                }
            }
            notifyMany(recipients,
                    Notification.TYPE_ENDORSEMENT_REQUESTED,
                    "Đã nhận yêu cầu điều chỉnh hợp đồng",
                    msg.toString(),
                    link,
                    absoluteMailLink(link, policy.getPolicyNumber()));
        } catch (Exception e) {
            log.warn("Failed to create ENDORSEMENT_REQUESTED notification for policy {}: {}",
                    policy != null ? policy.getPolicyNumber() : null, e.getMessage());
        }
    }

    @Override
    public void notifyClaimApproved(Claim claim, Policy policy) {
        try {
            if (claim == null || claim.getId() == null) {
                return;
            }
            String link = "/claims/" + claim.getId();
            StringBuilder msg = new StringBuilder("Yêu cầu bồi thường " + claim.getId()
                    + " cho hợp đồng " + claim.getPolicyNumber() + " đã được chấp thuận.");
            if (claim.getClaimedAmount() != null) {
                msg.append(" Số tiền yêu cầu: ").append(formatVnd(claim.getClaimedAmount())).append(" VND.");
            }
            if (claim.getApprovedAmount() != null) {
                msg.append(" Số tiền được duyệt: ").append(formatVnd(claim.getApprovedAmount())).append(" VND.");
            }
            if (claim.getHandlerNote() != null && !claim.getHandlerNote().isBlank()) {
                msg.append(" Ghi chú xử lý: ").append(claim.getHandlerNote());
            }
            msg.append(" Bước tiếp theo: hồ sơ đang chờ chi trả (chưa phải đã chi trả).");
            List<String> recipients;
            if (policy != null && policy.getPolicyNumber() != null) {
                recipients = policyRecipients(policy, null);
            } else if (claim.getClaimantEmail() != null && !claim.getClaimantEmail().isBlank()) {
                recipients = List.of(claim.getClaimantEmail().trim());
            } else {
                return;
            }
            notifyMany(recipients,
                    Notification.TYPE_CLAIM_APPROVED,
                    "Yêu cầu bồi thường đã được chấp thuận",
                    msg.toString(),
                    link,
                    absoluteMailLink(link, claim.getPolicyNumber()));
        } catch (Exception e) {
            log.warn("Failed to create CLAIM_APPROVED notification for claim {}: {}",
                    claim != null ? claim.getId() : null, e.getMessage());
        }
    }

    /** Tất cả user ROLE_UNDERWRITER (pattern của notifyNewOffer nhánh phe cầu), loại trừ actor. */
    private List<String> underwriterRecipients(String actorEmail) {
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
            log.warn("Failed to resolve underwriter recipients: {}", e.getMessage());
        }
        return underwriters;
    }

    /**
     * Link tuyệt đối cho mail, hoặc chuỗi chỉ ghi số hợp đồng khi
     * FRONTEND_URL chưa set (không hardcode domain prod).
     */
    private String absoluteMailLink(String relativeLink, String policyNumber) {
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String base = frontendUrl.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + relativeLink;
        }
        return "Hợp đồng " + policyNumber;
    }

    private static String formatVnd(Double amount) {
        if (amount == null) {
            return "";
        }
        long vnd = Math.round(amount);
        return String.format("%,d", vnd).replace(',', '.');
    }

    /**
     * Gửi cho từng recipient, mỗi recipient try/catch riêng: một recipient lỗi
     * không chặn các recipient khác, và không bao giờ throw ra caller.
     * Email chỉ best-effort (MailService tự catch SMTP + null sender; ở đây
     * vẫn bọc thêm try/catch).
     */
    private void notifyMany(List<String> recipients, String type, String title, String message, String link) {
        notifyMany(recipients, type, title, message, link, link);
    }

    /**
     * Biến thể: in-app notification giữ link tương đối, còn mail dùng link
     * tuyệt đối (mailLink) — hoặc chuỗi ghi số hợp đồng khi FRONTEND_URL
     * chưa set. Các method notify* cũ vẫn dùng overload 5 tham số nên hành
     * vi cũ không đổi.
     */
    private void notifyMany(List<String> recipients, String type, String title, String message, String link, String mailLink) {
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
                    mailService.sendNotificationEmail(recipient, title, message, mailLink);
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
