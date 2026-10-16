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

/**
 * Xử lý sau khi đăng nhập Google OAuth2 thành công:
 * upsert user, cấp JWT nội bộ, redirect về frontend kèm token.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        if (email == null || email.isBlank()) {
            response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
            return;
        }
        AuthResponse auth = authService.loginWithOAuth2(email.trim().toLowerCase(), name);
        String target = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                .queryParam("token", auth.getToken())
                .build(true).toUriString();
        response.sendRedirect(target);
    }
}
