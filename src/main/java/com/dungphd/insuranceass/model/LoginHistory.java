package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Lịch sử đăng nhập (đợt 4D). Một document cho một lần đăng nhập THÀNH CÔNG.
 * Chỉ ghi khi xác thực thành công (password, OTP, Google); sai mật khẩu /
 * sai OTP / tài khoản bị khóa thì KHÔNG ghi. Dev quick-switch KHÔNG ghi.
 * Không bao giờ chứa mật khẩu hay OTP.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "login_history")
public class LoginHistory {

    @Id
    private String id;

    /** Email đã chuẩn hóa lowercase của tài khoản đăng nhập. */
    private String email;

    /** Thời điểm đăng nhập thành công (server clock). */
    private Instant loggedInAt;

    /** IP client (đã qua X-Forwarded-For, hoặc "unknown" nếu không lấy được). */
    private String ip;

    /** User-Agent nguyên văn của client (có thể null nếu client không gửi). */
    private String userAgent;
}
