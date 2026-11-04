package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.response.PaymentResponse;

import java.util.List;
import java.util.Map;

/**
 * Thanh toán qua cổng VNPay (đợt payment). Return URL không bao giờ đổi
 * trạng thái — chỉ IPN mới đổi trạng thái (ANALYSIS §2.3, quyết định 7).
 */
public interface PaymentService {

    /**
     * Tạo payment cho policy QUOTED, chỉ bên cầu được tạo.
     * Lỗi: policy không tồn tại → 404; không phải bên cầu → 403;
     * policy không QUOTED / giá deal lệch → 409/400; cổng chưa cấu hình → 503.
     */
    PaymentResponse createPayment(String policyNumber, String principal, String clientIp);

    /** Lịch sử payment của một policy, scope theo rule đọc policy hiện tại. */
    List<PaymentResponse> listPayments(String policyNumber, String principal);

    /**
     * Tra cứu trạng thái để FE hiển thị ở trang kết quả return URL.
     * CHỈ ĐỌC: không đổi Payment, không đổi Policy, không gửi mail.
     * Nếu FE gửi kèm vnp_SecureHash mà verify fail → 403 (chống giả mạo hiển thị).
     */
    PaymentResponse lookupReturn(String txnRef, Map<String, String> vnpParams, String principal);

    /**
     * Xử lý IPN server-to-server từ VNPay. Trả về đúng format VNPay yêu cầu:
     * map {"RspCode": ..., "Message": ...} — KHÔNG bọc ApiResponse envelope.
     * RspCode: 00 = đã nhận/xử lý thành công (kể cả IPN trùng khi đã PAID);
     * 97 = sai chữ ký; 01 = không tìm thấy txnRef; 04 = sai số tiền;
     * 02 = payment đã xử lý xong ở trạng thái cuối khác (FAILED/CANCELLED);
     * 99 = các trường hợp còn lại (vd policy không còn QUOTED — giữ payment
     * PENDING để đối soát tay, không tự ACTIVE, không tự hoàn tiền).
     */
    Map<String, String> handleIpn(Map<String, String> vnpParams);
}
