package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.config.JwtTokenProvider;
import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginHistoryTest {

    @Mock
    private OtpService otpService;

    @Mock
    private MailService mailService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginHistoryService loginHistoryService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "adminEmailsConfig", "dungphdse@gmail.com,admin@insurtech.vn");
        ReflectionTestUtils.setField(authService, "devQuickSwitchEnabled", true);
    }

    private User passwordUser(String email) {
        return User.builder()
                .email(email)
                .fullName("Người Dùng Mẫu")
                .password("hashed-password")
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(true)
                .build();
    }

    private void stubJwt(String email, String token) {
        when(jwtTokenProvider.generateToken(eq(email), any(), any())).thenReturn(token);
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);
    }

    // ---- Đường thành công 1: password ----

    @Test
    void login_PasswordSuccess_RecordsLoginWithRequestContext() {
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.of(passwordUser("agent@example.com")));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(true);
        stubJwt("agent@example.com", "agent-jwt");

        assertNotNull(authService.login(AuthLoginRequest.builder()
                .email("agent@example.com").password("Password123!").build(),
                "203.0.113.7", "Chrome/130"));

        verify(loginHistoryService).recordSuccessfulLogin("agent@example.com", "203.0.113.7", "Chrome/130");
    }

    @Test
    void login_PasswordFailure_DoesNotRecord() {
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.of(passwordUser("agent@example.com")));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.login(AuthLoginRequest.builder()
                .email("agent@example.com").password("Password123!").build(),
                "203.0.113.7", "Chrome/130"));

        verify(loginHistoryService, never()).recordSuccessfulLogin(any(), any(), any());
    }

    @Test
    void login_PasswordSuccess_RecorderFailureDoesNotFailLogin() {
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.of(passwordUser("agent@example.com")));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(true);
        stubJwt("agent@example.com", "agent-jwt");
        doThrow(new RuntimeException("mongo down")).when(loginHistoryService)
                .recordSuccessfulLogin(any(), any(), any());

        // Ghi lịch sử lỗi vẫn phải trả JWT bình thường (failure-isolated).
        assertNotNull(authService.login(AuthLoginRequest.builder()
                .email("agent@example.com").password("Password123!").build(),
                "203.0.113.7", "Chrome/130"));
    }

    // ---- Đường thành công 2: OTP (cả /auth/verify-otp lẫn /auth/login) ----

    @Test
    void verifyOtp_Success_RecordsLogin() {
        when(otpService.validateOtp("user@gmail.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubJwt("user@gmail.com", "mock-jwt-token");

        assertNotNull(authService.verifyOtp(new VerifyOtpRequest("user@gmail.com", "123456"),
                "198.51.100.9", "Firefox/130"));

        verify(loginHistoryService).recordSuccessfulLogin("user@gmail.com", "198.51.100.9", "Firefox/130");
    }

    @Test
    void verifyOtp_InvalidOtp_DoesNotRecord() {
        when(otpService.validateOtp("user@gmail.com", "999999")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                authService.verifyOtp(new VerifyOtpRequest("user@gmail.com", "999999"),
                        "198.51.100.9", "Firefox/130"));

        verify(loginHistoryService, never()).recordSuccessfulLogin(any(), any(), any());
    }

    @Test
    void login_OtpSuccess_RecordsLogin() {
        when(otpService.validateOtp("user@gmail.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubJwt("user@gmail.com", "mock-jwt-token");

        assertNotNull(authService.login(AuthLoginRequest.builder()
                .email("user@gmail.com").otp("123456").build(),
                "198.51.100.9", "Firefox/130"));

        verify(loginHistoryService).recordSuccessfulLogin("user@gmail.com", "198.51.100.9", "Firefox/130");
    }

    // ---- Đường thành công 3: Google ----

    @Test
    void loginWithOAuth2_Success_RecordsLogin() {
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubJwt("oauth@example.com", "oauth-jwt");

        assertNotNull(authService.loginWithOAuth2("oauth@example.com", "OAuth User",
                "192.0.2.5", "Safari/17"));

        verify(loginHistoryService).recordSuccessfulLogin("oauth@example.com", "192.0.2.5", "Safari/17");
    }

    // ---- Dev quick-switch: KHÔNG ghi ----

    @Test
    void login_DevMode_DoesNotRecord() {
        when(userRepository.findByEmail("user@insurtech.vn")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubJwt("user@insurtech.vn", "dev-jwt");

        assertNotNull(authService.login(AuthLoginRequest.builder().role("ROLE_CUSTOMER").build(),
                "127.0.0.1", "DevBrowser"));

        verify(loginHistoryService, never()).recordSuccessfulLogin(any(), any(), any());
    }
}
