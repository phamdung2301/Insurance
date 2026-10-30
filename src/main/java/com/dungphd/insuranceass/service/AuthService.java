package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;

public interface AuthService {

    void sendOtp(SendOtpRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    /**
     * Biến thể có ngữ cảnh request (đợt 4D): controller trích xuất ip +
     * userAgent từ HttpServletRequest rồi truyền vào, để service ghi lịch
     * sử đăng nhập mà không chạm tới HttpServletRequest. Bản 1 tham số
     * (tương thích ngược) ủy quyền về đây với ip = "unknown".
     */
    AuthResponse verifyOtp(VerifyOtpRequest request, String ip, String userAgent);

    AuthResponse login(AuthLoginRequest request);

    /** Như {@link #verifyOtp(VerifyOtpRequest, String, String)} nhưng cho login(). */
    AuthResponse login(AuthLoginRequest request, String ip, String userAgent);

    UserProfileResponse getMe(String email);

    AuthResponse loginWithOAuth2(String email, String fullName);

    /** Như {@link #loginWithOAuth2(String, String)} nhưng có ip + userAgent. */
    AuthResponse loginWithOAuth2(String email, String fullName, String ip, String userAgent);
}
