package com.dungphd.insuranceass.controller;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.GlobalExceptionHandler;
import com.dungphd.insuranceass.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getProfile_WithPrincipal_Success() throws Exception {
        UserProfileResponse responseDto = UserProfileResponse.builder()
                .id("usr-123")
                .email("user@example.com")
                .fullName("John Doe")
                .phone("0987654321")
                .address("123 Street")
                .roles(List.of("ROLE_USER"))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(userService.getProfile("user@example.com")).thenReturn(responseDto);

        mockMvc.perform(get("/user/profile")
                        .principal(() -> "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("John Doe"));
    }

    @Test
    void getProfile_WithHeader_Success() throws Exception {
        UserProfileResponse responseDto = UserProfileResponse.builder()
                .id("usr-123")
                .email("header@example.com")
                .fullName("Jane Doe")
                .build();

        when(userService.getProfile("header@example.com")).thenReturn(responseDto);

        mockMvc.perform(get("/user/profile")
                        .header("X-User-Email", "header@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("header@example.com"));
    }

    @Test
    void updateProfile_Success() throws Exception {
        UserProfileResponse responseDto = UserProfileResponse.builder()
                .id("usr-123")
                .email("user@example.com")
                .fullName("Updated Name")
                .phone("0987654321")
                .address("New Address 456")
                .build();

        when(userService.updateProfile(eq("user@example.com"), any(UpdateProfileRequest.class)))
                .thenReturn(responseDto);

        String requestJson = "{\"fullName\":\"Updated Name\",\"phone\":\"0987654321\",\"address\":\"New Address 456\"}";

        mockMvc.perform(put("/user/profile")
                        .principal(() -> "user@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("0987654321"));
    }

    @Test
    void updateProfile_ValidationFailure() throws Exception {
        String invalidRequestJson = "{\"fullName\":\"\",\"phone\":\"invalid-phone\"}";

        mockMvc.perform(put("/user/profile")
                        .principal(() -> "user@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.fullName").exists());
    }

    @Test
    void changePassword_Success() throws Exception {
        doNothing().when(userService).changePassword(eq("user@example.com"), any(ChangePasswordRequest.class));

        String requestJson = "{\"oldPassword\":\"CurrentPassword123\",\"newPassword\":\"NewSecurePassword123\",\"confirmPassword\":\"NewSecurePassword123\"}";

        mockMvc.perform(post("/user/change-password")
                        .principal(() -> "user@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void changePassword_ValidationFailure_ShortPassword() throws Exception {
        String invalidRequestJson = "{\"oldPassword\":\"OldPass\",\"newPassword\":\"123\"}";

        mockMvc.perform(post("/user/change-password")
                        .principal(() -> "user@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.newPassword").exists());
    }
}
