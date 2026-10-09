package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.config.JwtTokenProvider;
import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
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
}
