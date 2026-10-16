package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý sau khi đăng nhập Google OAuth2 thành công:
 * upsert user, cấp JWT nội bộ, redirect về frontend kèm token.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthService authService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String email = oauthUser.getAttribute("email");
            String name = oauthUser.getAttribute("name");
            if (email == null || email.isBlank()) {
                log.warn("[OAuth2] Google không trả về email, hủy đăng nhập");
                response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
                return;
            }
            AuthResponse auth = authService.loginWithOAuth2(email.trim().toLowerCase(), name);
            log.info("[OAuth2] Đăng nhập Google thành công: {}", auth.getEmail());
            // Truyền cả user info qua URL để frontend không cần gọi thêm /auth/me
            String target = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                    .queryParam("token", auth.getToken())
                    .queryParam("email", auth.getEmail())
                    .queryParam("name", auth.getFullName())
                    .queryParam("roles", String.join(",", auth.getRoles()))
                    .build(true).toUriString();
            response.sendRedirect(target);
        } catch (Exception e) {
            // Không bao giờ để lộ Whitelabel 500 cho user — redirect về login kèm lỗi thân thiện
            log.error("[OAuth2] Lỗi xử lý callback Google", e);
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        }
    }
}
