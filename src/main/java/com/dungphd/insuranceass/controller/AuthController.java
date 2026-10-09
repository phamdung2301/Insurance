package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.ApiResponse;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping({"/auth", "/api/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/send-otp
     * Request 6-digit random OTP sent to Gmail
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Mã OTP đã được gửi về hòm thư " + request.getEmail() + ". Vui lòng kiểm tra hộp thư đến.",
                "OTP sent successfully"
        ));
    }

    /**
     * POST /auth/verify-otp
     * Verify 6-digit OTP and return JWT token
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Xác thực OTP thành công, đăng nhập hoàn tất"));
    }

    /**
     * POST /auth/login
     * Supports both OTP login and Dev Quick Switch (ROLE_USER / ROLE_ADMIN)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthLoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đăng nhập thành công"));
    }

    /**
     * GET /auth/me
     * Fetch current authenticated user info from token
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            Principal principal,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail
    ) {
        String email = null;
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            email = principal.getName();
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                email = auth.getName();
            } else if (headerEmail != null && !headerEmail.isBlank()) {
                email = headerEmail.trim();
            }
        }

        UserProfileResponse me = authService.getMe(email);
        return ResponseEntity.ok(ApiResponse.success(me, "Thông tin tài khoản đã xác thực"));
    }
}
