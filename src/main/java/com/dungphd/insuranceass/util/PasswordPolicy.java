package com.dungphd.insuranceass.util;

/**
 * Chuẩn mật khẩu dùng chung cho mọi đường tạo/đổi mật khẩu (đổi mật khẩu,
 * ADMIN tạo user, agent tạo customer). Hằng số static để dùng trực tiếp
 * trong annotation bean-validation (@Pattern cần hằng compile-time).
 *
 * Quy tắc: 8–50 ký tự, bắt buộc có cả chữ VÀ số (không ép ký tự đặc biệt
 * để không làm khó khách hàng cá nhân — độ dài 8+ đã đủ an toàn cơ bản
 * với rate-limit + khoá tài khoản đã có ở OtpService).
 */
public final class PasswordPolicy {

    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,50}$";

    public static final String VIOLATION_MESSAGE =
            "Mật khẩu phải có độ dài 8–50 ký tự và bao gồm cả chữ cái và số.";

    private PasswordPolicy() {
    }
}
