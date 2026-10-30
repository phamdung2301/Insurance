package com.dungphd.insuranceass.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Trích xuất thông tin client từ HttpServletRequest — CHỈ dùng ở tầng edge
 * (controller / AuthenticationSuccessHandler), nơi request còn tồn tại.
 * Service layer không bao giờ chạm tới HttpServletRequest: controller/handler
 * trích xuất xong rồi truyền ip + userAgent vào service dưới dạng String.
 *
 * <p>Quy tắc IP: header {@code X-Forwarded-For} nếu có thì lấy giá trị ĐẦU
 * TIÊN (trước dấu phẩy, trim) — đó là IP client gốc khi đi qua proxy/load
 * balancer; nếu không có header thì dùng {@code remoteAddr}; nếu cả hai
 * đều không lấy được (request null) thì trả "unknown".
 */
public final class ClientRequestInfo {

    /** Giá trị ip khi không lấy được request / không có địa chỉ nào. */
    public static final String UNKNOWN_IP = "unknown";

    private ClientRequestInfo() {
    }

    public static String extractIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", -1)[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? UNKNOWN_IP : remoteAddr.trim();
    }

    /** User-Agent nguyên văn; null nếu request null hoặc client không gửi header. */
    public static String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        return (userAgent == null || userAgent.isBlank()) ? null : userAgent;
    }
}
