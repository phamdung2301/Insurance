package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.PaymentResponse;

import java.util.List;

/**
 * Thanh toán qua cổng SePay (chuyển khoản + webhook). Webhook là nguồn sự
 * thật duy nhất đổi trạng thái — không có trang return nào đổi trạng thái.
 * Endpoint tra cứu trạng thái CHỈ ĐỌC: không đổi Payment, không đổi Policy,
 * không gửi mail.
 */
public interface PaymentService {

    /** Kết quả xử lý webhook SePay: HTTP status trả về + body success. */
    record WebhookResult(int httpStatus, boolean success) {
        public static WebhookResult ok() {
            return new WebhookResult(200, true);
        }

        public static WebhookResult unauthorized() {
            return new WebhookResult(401, false);
        }
    }

    /**
     * Tạo payment cho policy QUOTED, chỉ bên cầu được tạo.
     * Lỗi: policy không tồn tại → 404; không phải bên cầu → 403;
     * policy không QUOTED / giá deal lệch → 409/400; cổng chưa cấu hình
     * (thiếu bank/acc) → 503. Response 201 kèm transferContent + QR.
     */
    PaymentResponse createPayment(String policyNumber, String principal);

    /** Lịch sử payment của một policy, scope theo rule đọc policy hiện tại. */
    List<PaymentResponse> listPayments(String policyNumber, String principal);

    /**
     * Tra cứu trạng thái để FE hiển thị sau khi khách chuyển khoản.
     * CHỈ ĐỌC: không đổi Payment, không đổi Policy, không gửi mail.
     * Yêu cầu JWT + đúng scope đọc policy (controller/service enforce).
     */
    PaymentResponse lookupStatus(String txnRef, String principal);

    /**
     * Xử lý webhook SePay "Có tiền vào" (POST JSON, server-to-server,
     * KHÔNG có JWT — an toàn dựa vào verify HMAC-SHA256 trên RAW BODY).
     *
     * <p>Quy tắc trả về (SePay retry tối đa 7 lần trong 5h nếu không nhận
     * 200/201 + {@code {"success": true}} trong 30s, nên chỉ lỗi xác thực
     * mới trả 401; mọi lỗi dữ liệu đều trả 200 success + log, vì retry
     * cũng vô ích):
     * <ul>
     *   <li>401: sai/thiếu chữ ký, thiếu secret cấu hình, timestamp hết hạn.</li>
     *   <li>200 success + log, không đổi trạng thái: body parse lỗi,
     *       transferType != "in", không khớp payment nào, accountNumber lệch,
     *       sai số tiền (payment GIỮ PENDING để đối soát tay), webhook trùng
     *       (SePay id đã xử lý / payment đã ở trạng thái cuối), policy không
     *       còn QUOTED (payment GIỮ PENDING để đối soát tay).</li>
     *   <li>200 success + chuyển trạng thái: chỉ khi payment PENDING + policy
     *       QUOTED → QUOTED → ACTIVE, paymentStatus=CONFIRMED, ledger
     *       STATUS_TRANSITION, lưu gatewayTransactionNo=SePay id, gửi mail
     *       payment-success + in-app notification (tái dùng trigger cũ).</li>
     * </ul>
     * Không bao giờ tạo payment/policy mới từ webhook.
     */
    WebhookResult handleSepayWebhook(byte[] rawBody, String signatureHeader, String timestampHeader);
}
