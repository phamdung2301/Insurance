package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.config.JwtTokenProvider;
import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
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
class AuthServiceImplTest {

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

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "adminEmailsConfig", "dungphdse@gmail.com,admin@insurtech.vn");
    }

    @Test
    void sendOtp_Success() {
        when(otpService.generateOtp("test@gmail.com")).thenReturn("123456");

        authService.sendOtp(new SendOtpRequest("test@gmail.com"));

        verify(otpService).generateOtp("test@gmail.com");
        verify(mailService).sendOtpEmail("test@gmail.com", "123456");
    }

    @Test
    void verifyOtp_Success_NormalUser() {
        when(otpService.validateOtp("user@gmail.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq("user@gmail.com"), any(), any())).thenReturn("mock-jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.verifyOtp(new VerifyOtpRequest("user@gmail.com", "123456"));

        assertNotNull(response);
        assertEquals("user@gmail.com", response.getEmail());
        assertEquals("mock-jwt-token", response.getToken());
        assertTrue(response.getRoles().contains("ROLE_USER"));
        assertFalse(response.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    void verifyOtp_Success_AdminUser() {
        when(otpService.validateOtp("dungphdse@gmail.com", "654321")).thenReturn(true);
        when(userRepository.findByEmail("dungphdse@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq("dungphdse@gmail.com"), any(), any())).thenReturn("admin-jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.verifyOtp(new VerifyOtpRequest("dungphdse@gmail.com", "654321"));

        assertNotNull(response);
        assertEquals("dungphdse@gmail.com", response.getEmail());
        assertTrue(response.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    void verifyOtp_InvalidOtp_ThrowsBadRequest() {
        when(otpService.validateOtp("user@gmail.com", "999999")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                authService.verifyOtp(new VerifyOtpRequest("user@gmail.com", "999999"))
        );
    }

    @Test
    void login_DevMode_Admin() {
        when(userRepository.findByEmail("admin@insurtech.vn")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq("admin@insurtech.vn"), any(), any())).thenReturn("admin-jwt");

        AuthLoginRequest request = AuthLoginRequest.builder()
                .role("ROLE_ADMIN")
                .build();

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("admin@insurtech.vn", response.getEmail());
        assertTrue(response.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    void login_Password_Success() {
        User user = User.builder()
                .email("agent@example.com")
                .fullName("Đại Lý Mẫu")
                .password("hashed-password")
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(true)
                .build();

        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(eq("agent@example.com"), any(), any())).thenReturn("agent-jwt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(AuthLoginRequest.builder()
                .email("agent@example.com")
                .password("Password123!")
                .build());

        assertNotNull(response);
        assertEquals("agent-jwt", response.getToken());
        assertTrue(response.getRoles().contains("ROLE_AGENT"));
    }

    @Test
    void login_Password_WrongPassword() {
        User user = User.builder()
                .email("agent@example.com")
                .password("hashed-password")
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(true)
                .build();

        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build())
        );
    }

    @Test
    void login_Password_UnknownEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("ghost@example.com")
                        .password("Password123!")
                        .build())
        );
    }

    @Test
    void login_Password_Disabled() {
        User user = User.builder()
                .email("agent@example.com")
                .password("hashed-password")
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(false)
                .build();

        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build())
        );
    }

    @Test
    void login_Password_NullStoredPassword() {
        User user = User.builder()
                .email("agent@example.com")
                .password(null)
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(true)
                .build();

        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build())
        );
    }

    @Test
    void getMe_Success() {
        User user = User.builder()
                .id("u1")
                .email("test@gmail.com")
                .fullName("Test User")
                .roles(new ArrayList<>(List.of("ROLE_USER")))
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));

        UserProfileResponse me = authService.getMe("test@gmail.com");

        assertNotNull(me);
        assertEquals("test@gmail.com", me.getEmail());
        assertEquals("Test User", me.getFullName());
    }

    // ---- Đợt 1A, L5: khóa password login ----

    private User passwordUser(String email) {
        return User.builder()
                .email(email)
                .fullName("Người Dùng Mẫu")
                .password("hashed-password")
                .roles(new ArrayList<>(List.of("ROLE_AGENT")))
                .enabled(true)
                .build();
    }

    @Test
    void login_Password_WrongPassword_RecordsFailedAttempt() {
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.of(passwordUser("agent@example.com")));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build()));

        verify(otpService).recordFailedPasswordAttempt("agent@example.com");
    }

    @Test
    void login_Password_UnknownEmail_DoesNotRecordFailedAttempt() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("ghost@example.com")
                        .password("Password123!")
                        .build()));

        verify(otpService, never()).recordFailedPasswordAttempt(any());
    }

    @Test
    void login_Password_Success_ClearsLockoutCounter() {
        when(userRepository.findByEmail("agent@example.com"))
                .thenReturn(Optional.of(passwordUser("agent@example.com")));
        when(passwordEncoder.matches("Password123!", "hashed-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(eq("agent@example.com"), any(), any())).thenReturn("agent-jwt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(AuthLoginRequest.builder()
                .email("agent@example.com")
                .password("Password123!")
                .build());

        assertNotNull(response);
        verify(otpService).clearPasswordLockout("agent@example.com");
    }

    @Test
    void login_Password_WhenLocked_RejectsEvenWithCorrectPassword() {
        // Khóa được kiểm tra TRƯỚC mọi bước khác (không load user, không
        // verify BCrypt): vì vậy ở đây chỉ stub isPasswordLocked — một user
        // có mật khẩu đúng ngoài đời thực vẫn bị chặn ở bước này, và test
        // chứng minh đúng thứ tự đó (không stub matches/findByEmail vì code
        // không được phép chạm tới chúng khi đang khóa; Mockito strict sẽ
        // báo UnnecessaryStubbing nếu stub thừa).
        when(otpService.isPasswordLocked("agent@example.com")).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build()));

        assertEquals("Tài khoản tạm khóa, vui lòng thử lại sau.", ex.getMessage());
        verify(jwtTokenProvider, never()).generateToken(any(), any(), any());
    }

    @Test
    void login_Password_Disabled_UsesSameUnifiedLockMessage() {
        User disabled = passwordUser("agent@example.com");
        disabled.setEnabled(false);
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(disabled));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                authService.login(AuthLoginRequest.builder()
                        .email("agent@example.com")
                        .password("Password123!")
                        .build()));

        // Nhánh password không lộ phân biệt disable vs khóa tạm.
        assertEquals("Tài khoản tạm khóa, vui lòng thử lại sau.", ex.getMessage());
    }

    @Test
    void verifyOtp_Success_ClearsPasswordLockout() {
        when(otpService.validateOtp("user@gmail.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq("user@gmail.com"), any(), any())).thenReturn("mock-jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        assertNotNull(authService.verifyOtp(new VerifyOtpRequest("user@gmail.com", "123456")));
        verify(otpService).clearPasswordLockout("user@gmail.com");
    }

    @Test
    void loginWithOAuth2_Success_ClearsPasswordLockout() {
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq("oauth@example.com"), any(), any())).thenReturn("oauth-jwt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        assertNotNull(authService.loginWithOAuth2("oauth@example.com", "OAuth User"));
        verify(otpService).clearPasswordLockout("oauth@example.com");
    }
}
