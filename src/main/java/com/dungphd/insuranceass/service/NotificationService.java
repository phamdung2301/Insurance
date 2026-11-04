package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.EndorsementRequest;
import com.dungphd.insuranceass.model.Claim;
import com.dungphd.insuranceass.model.NegotiationRole;
import com.dungphd.insuranceass.model.Notification;
import com.dungphd.insuranceass.model.Payment;
import com.dungphd.insuranceass.model.Policy;

import java.util.List;

/**
 * Thông báo (đợt 2B).
 *
 * <p>Các method notify* là tác dụng phụ FAILURE-ISOLATED: mọi exception khi
 * lưu notification hoặc gửi email đều bị catch + log warn bên trong impl,
 * KHÔNG propagate — thao tác chính (offer, chuyển trạng thái, endorsement)
 * không bao giờ fail vì notification. Caller vì vậy không cần try/catch thêm.
 *
 * <p>Các method đọc/ghi trạng thái (list/unreadCount/markAsRead) thì ngược
 * lại: scope chặt theo principal, không có principal thì throw (fail-closed),
 * mark-as-read sai chủ → ResourceNotFoundException (HTTP 404).
 */
public interface NotificationService {

    /**
     * Gửi thông báo về offer mới cho PHE TỚI LƯỢT.
     * Offer từ phe cầu (CUSTOMER/AGENT) → tất cả user ROLE_UNDERWRITER.
     * Offer từ UNDERWRITER → chủ policy + assignedAgentEmail (loại trùng, loại trừ actor).
     */
    void notifyNewOffer(Policy policy, NegotiationRole offerRole, String actorEmail);

    /** Sau khi policy chuyển BOUND → ACTIVE: chủ policy + assignedAgentEmail. */
    void notifyPolicyActivated(Policy policy);

    /** Sau khi endorsement apply thành công: chủ policy + assignedAgentEmail. */
    void notifyEndorsementApplied(Policy policy);

    /**
     * Đợt payment — IPN thành công, policy vừa QUOTED → ACTIVE qua cổng.
     * Người nhận: bên cầu (insured + assignedAgentEmail). Đây là mail "thông
     * tin đơn hàng" (số tiền, mã giao dịch, ngày hiệu lực/hết hạn). CỐ TÌNH
     * không gọi thêm notifyPolicyActivated ở nhánh IPN để tránh gửi trùng
     * 2 notification cùng nội dung kích hoạt cho cùng người nhận.
     */
    void notifyPaymentSucceeded(Policy policy, Payment payment);

    /**
     * Đợt payment — khách tạo yêu cầu endorsement. Người nhận: bên cầu
     * (xác nhận đã nhận yêu cầu) + tất cả ROLE_UNDERWRITER (có việc cần xử
     * lý). Các tham số pending chỉ có ở nhánh pending (&gt; 20%):
     * pendingId/premiumChangePct/oldPremium/projectedPremium; nhánh apply
     * trực tiếp truyền null cho các tham số đó và appliedImmediately = true.
     */
    void notifyEndorsementRequested(Policy policy, EndorsementRequest request, String actorEmail,
                                    String pendingId, Double premiumChangePct,
                                    Double oldPremium, Double projectedPremium,
                                    boolean appliedImmediately);

    /**
     * Đợt payment — claim được approve (chỉ bước approveClaim).
     * Người nhận: policyRecipients(policy) nếu tra được policy, nếu không
     * fallback về claim.claimantEmail duy nhất.
     */
    void notifyClaimApproved(Claim claim, Policy policy);

    /**
     * Nhac tai tuc (dot 3B): hop dong ACTIVE sap het han trong {@code daysBefore}
     * ngay. Nguoi nhan: chu policy + assignedAgentEmail (loai trung, nhu cac
     * method notify* khac). Failure-isolated nhu cac notify* khac: khong bao gio
     * throw ra caller (RenewalReminderService van boc them try/catch theo policy).
     */
    void notifyRenewalReminder(Policy policy, int daysBefore);

    /** Chỉ notification của đúng principal, mới nhất trước. */
    List<Notification> listForPrincipal(String principalEmail, boolean unreadOnly);

    /** Số notification chưa đọc của đúng principal. */
    long unreadCount(String principalEmail);

    /**
     * Đánh dấu đã đọc. Không phải của principal hoặc không tồn tại →
     * ResourceNotFoundException (404, không lộ sự tồn tại).
     */
    Notification markAsRead(String principalEmail, String id);
}
