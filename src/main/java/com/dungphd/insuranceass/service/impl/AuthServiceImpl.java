package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.config.JwtTokenProvider;
import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import com.dungphd.insuranceass.service.AuthService;
import com.dungphd.insuranceass.service.MailService;
import com.dungphd.insuranceass.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OtpService otpService;
    private final MailService mailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${app.admin.emails:dungphdse@gmail.com,admin@insurtech.vn}")
    private String adminEmailsConfig;

    @Override
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String otp = otpService.generateOtp(email);
        mailService.sendOtpEmail(email, otp);
        log.info("OTP dispatched for email: {}", email);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        boolean valid = otpService.validateOtp(email, request.getOtp());
        if (!valid) {
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn (300s). Vui lòng thử lại.");
        }

        User user = upsertUser(email, null, null);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(AuthLoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;

        // 1. Dev Login / Quick Switch mode
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String roleUpper = request.getRole().trim().toUpperCase();
            boolean isAdmin = roleUpper.contains("ADMIN");

            if (email == null || email.isBlank()) {
                email = isAdmin ? "admin@insurtech.vn" : "user@insurtech.vn";
            }

            List<String> explicitRoles = isAdmin
                    ? List.of("ROLE_ADMIN", "ROLE_USER")
                    : List.of("ROLE_USER");

            String fullName = request.getFullName() != null && !request.getFullName().isBlank()
                    ? request.getFullName()
                    : (isAdmin ? "Quản trị viên Hệ thống" : "Khách hàng Mẫu");

            User user = upsertUser(email, fullName, explicitRoles);
            return buildAuthResponse(user);
        }

        // 2. Regular OTP verification mode
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required for authentication");
        }

        if (request.getOtp() != null && !request.getOtp().isBlank()) {
            boolean valid = otpService.validateOtp(email, request.getOtp());
            if (!valid) {
                throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn.");
            }
        } else {
            throw new BadRequestException("OTP or Role must be provided for login");
        }

        User user = upsertUser(email, request.getFullName(), null);
        return buildAuthResponse(user);
    }

    @Override
    public UserProfileResponse getMe(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Authenticated email cannot be empty");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private User upsertUser(String email, String fullName, List<String> explicitRoles) {
        Optional<User> existing = userRepository.findByEmail(email);

        List<String> assignedRoles;
        if (explicitRoles != null && !explicitRoles.isEmpty()) {
            assignedRoles = new ArrayList<>(explicitRoles);
        } else if (isAdminEmail(email)) {
            assignedRoles = List.of("ROLE_ADMIN", "ROLE_USER");
        } else {
            assignedRoles = List.of("ROLE_USER");
        }

        if (existing.isPresent()) {
            User user = existing.get();
            // Update roles if needed
            for (String r : assignedRoles) {
                if (!user.getRoles().contains(r)) {
                    user.getRoles().add(r);
                }
            }
            if (fullName != null && !fullName.isBlank()) {
                user.setFullName(fullName);
            }
            user.setUpdatedAt(Instant.now());
            return userRepository.save(user);
        }

        String displayName = (fullName != null && !fullName.isBlank())
                ? fullName
                : email.substring(0, email.indexOf('@'));

        User newUser = User.builder()
                .email(email)
                .fullName(displayName)
                .roles(new ArrayList<>(assignedRoles))
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return userRepository.save(newUser);
    }

    private boolean isAdminEmail(String email) {
        if (adminEmailsConfig == null || adminEmailsConfig.isBlank()) {
            return false;
        }
        Set<String> adminSet = Arrays.stream(adminEmailsConfig.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return adminSet.contains(email.toLowerCase());
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRoles(), user.getFullName());
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }
}
