package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.ChangePasswordRequest;
import com.dungphd.insuranceass.dto.request.UpdateProfileRequest;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id("usr-123")
                .email("test@example.com")
                .password("encodedOldPassword")
                .fullName("Nguyen Van A")
                .phone("0987654321")
                .address("123 Le Loi, TP.HCM")
                .roles(List.of("ROLE_USER"))
                .enabled(true)
                .build();
    }

    @Test
    void getProfile_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        UserProfileResponse profile = userService.getProfile("test@example.com");

        assertNotNull(profile);
        assertEquals("test@example.com", profile.getEmail());
        assertEquals("Nguyen Van A", profile.getFullName());
        assertEquals("0987654321", profile.getPhone());
    }

    @Test
    void getProfile_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile("notfound@example.com"));
    }

    @Test
    void updateProfile_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Tran Van B")
                .phone("0912345678")
                .address("456 Nguyen Hue, TP.HCM")
                .build();

        UserProfileResponse response = userService.updateProfile("test@example.com", request);

        assertNotNull(response);
        assertEquals("Tran Van B", response.getFullName());
        assertEquals("0912345678", response.getPhone());
        assertEquals("456 Nguyen Hue, TP.HCM", response.getAddress());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void changePassword_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPassword123!", "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword123!", "encodedOldPassword")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("OldPassword123!")
                .newPassword("NewPassword123!")
                .confirmPassword("NewPassword123!")
                .build();

        assertDoesNotThrow(() -> userService.changePassword("test@example.com", request));

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "encodedOldPassword")).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("WrongPassword")
                .newPassword("NewPassword123!")
                .confirmPassword("NewPassword123!")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> userService.changePassword("test@example.com", request));
        assertEquals("Current password is incorrect", ex.getMessage());
    }

    @Test
    void changePassword_ConfirmPasswordMismatch_ThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("OldPassword123!")
                .newPassword("NewPassword123!")
                .confirmPassword("DifferentPassword123!")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> userService.changePassword("test@example.com", request));
        assertEquals("New password and confirm password do not match", ex.getMessage());
    }

    @Test
    void changePassword_SameAsOldPassword_ThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPassword123!", "encodedOldPassword")).thenReturn(true);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("OldPassword123!")
                .newPassword("OldPassword123!")
                .confirmPassword("OldPassword123!")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> userService.changePassword("test@example.com", request));
        assertEquals("New password cannot be the same as current password", ex.getMessage());
    }
}
