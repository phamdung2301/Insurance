package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendOtp_Success() throws Exception {
        doNothing().when(authService).sendOtp(any(SendOtpRequest.class));

        String json = "{\"email\":\"test@gmail.com\"}";

        mockMvc.perform(post("/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP sent successfully"));
    }

    @Test
    void verifyOtp_Success() throws Exception {
        AuthResponse authResponse = AuthResponse.builder()
                .token("jwt-token-xyz")
                .email("test@gmail.com")
                .roles(List.of("ROLE_USER"))
                .build();

        when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(authResponse);

        String json = "{\"email\":\"test@gmail.com\",\"otp\":\"123456\"}";

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token-xyz"))
                .andExpect(jsonPath("$.data.email").value("test@gmail.com"));
    }

    @Test
    void login_Success() throws Exception {
        AuthResponse authResponse = AuthResponse.builder()
                .token("jwt-admin-token")
                .email("admin@insurtech.vn")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .build();

        when(authService.login(any(AuthLoginRequest.class))).thenReturn(authResponse);

        String json = "{\"role\":\"ROLE_ADMIN\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-admin-token"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void getMe_Success() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .email("user@gmail.com")
                .fullName("User Name")
                .roles(List.of("ROLE_USER"))
                .build();

        when(authService.getMe("user@gmail.com")).thenReturn(profile);

        mockMvc.perform(get("/auth/me")
                        .header("X-User-Email", "user@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@gmail.com"));
    }
}
