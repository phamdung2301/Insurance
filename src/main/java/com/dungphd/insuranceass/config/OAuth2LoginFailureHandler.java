package com.dungphd.insuranceass.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Khi OAuth2 login thất bại ở tầng filter (sai state, code hết hạn, user từ chối...),
 * redirect về frontend kèm mã lỗi thân thiện thay vì để Spring đá về /login?error trên backend.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("[OAuth2] Đăng nhập Google thất bại: {}", exception.getMessage());
        response.sendRedirect(frontendBase() + "/login?error=oauth_failed");
    }

    /**
     * Chuẩn hóa frontend URL: tự thêm https:// nếu người dùng quên ghi protocol
     * (lỗi thường gặp khi set env FRONTEND_URL trên dashboard).
     */
    private String frontendBase() {
        String base = frontendUrl != null ? frontendUrl.trim() : "";
        if (!base.isEmpty() && !base.matches("(?i)^https?://.*")) {
            base = "https://" + base;
        }
        return base.replaceAll("/+$", "");
    }
}
