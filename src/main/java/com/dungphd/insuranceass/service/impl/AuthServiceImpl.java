package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.config.JwtTokenProvider;
import com.dungphd.insuranceass.dto.request.AuthLoginRequest;
import com.dungphd.insuranceass.dto.request.SendOtpRequest;
import com.dungphd.insuranceass.dto.request.VerifyOtpRequest;
import com.dungphd.insuranceass.dto.response.AuthResponse;
import com.dungphd.insuranceass.dto.response.UserProfileResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ForbiddenException;
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

    @Value("${app.dev.quick-switch-enabled:false}")
    private boolean devQuickSwitchEnabled;

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

        assertNotDisabled(email);
        User user = upsertUser(email, null, null);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(AuthLoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;

        // 1. Dev Login / Quick Switch mode (chỉ khi bật cờ app.dev.quick-switch-enabled)
        if (request.getRole() != null && !request.getRole().isBlank()) {
            if (!devQuickSwitchEnabled) {
                throw new BadRequestException("Dev quick-switch đã bị tắt. Vui lòng đăng nhập bằng OTP.");
            }
            // Allowlist khớp CHÍNH XÁC 5 role (PLAN §1.7): không dùng contains("ADMIN")
            // nữa — chuỗi lạ chứa "ADMIN" không còn qua được.
            String roleRaw = request.getRole().trim().toUpperCase();
            String role = roleRaw.startsWith("ROLE_") ? roleRaw : "ROLE_" + roleRaw;
            Set<String> allowedQuickSwitchRoles = Set.of(
                    "ROLE_CUSTOMER", "ROLE_AGENT", "ROLE_UNDERWRITER", "ROLE_FINANCE", "ROLE_ADMIN");
            if (!allowedQuickSwitchRoles.contains(role)) {
                throw new BadRequestException("Role quick-switch không hợp lệ: " + request.getRole());
            }
            boolean isAdmin = "ROLE_ADMIN".equals(role);

            if (email == null || email.isBlank()) {
                email = switch (role) {
                    case "ROLE_ADMIN" -> "admin@insurtech.vn";
                    case "ROLE_AGENT" -> "agent@insurtech.vn";
                    case "ROLE_UNDERWRITER" -> "uw@insurtech.vn";
                    case "ROLE_FINANCE" -> "finance@insurtech.vn";
                    default -> "user@insurtech.vn";
                };
            }

            List<String> explicitRoles = isAdmin
                    ? List.of("ROLE_ADMIN", "ROLE_USER")
                    : List.of(role);

            String fullName = request.getFullName() != null && !request.getFullName().isBlank()
                    ? request.getFullName()
                    : (isAdmin ? "Quản trị viên Hệ thống" : "Khách hàng Mẫu");

            assertNotDisabled(email);
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

        assertNotDisabled(email);
        User user = upsertUser(email, request.getFullName(), null);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse loginWithOAuth2(String email, String fullName) {
        String normalized = email != null ? email.trim().toLowerCase() : null;
        assertNotDisabled(normalized);
        // upsert với explicitRoles=null -> chỉ cấp ROLE_ADMIN lúc TẠO MỚI nếu email
        // nằm trong app.admin.emails (xem upsertUser); login sau không tự cộng lại.
        User user = upsertUser(normalized, fullName, null);
        return buildAuthResponse(user);
    }

    /** Tài khoản bị khóa → 403 ở cả 3 login path (PLAN §1.7). */
    private void assertNotDisabled(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .ifPresent(existing -> {
                    if (Boolean.FALSE.equals(existing.getEnabled())) {
                        throw new ForbiddenException("Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
                    }
                });
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
            // Null-safe: document cũ có thể không có roles
            if (user.getRoles() == null) {
                user.setRoles(new ArrayList<>());
            }
            // ADMIN_EMAILS chỉ gán lúc TẠO MỚI (PLAN §1.7): user đã tồn tại thì
            // KHÔNG tự cộng role từ env nữa — quyết định gỡ role qua màn admin
            // phải có hiệu lực thật. Chỉ explicitRoles (dev quick-switch) mới
            // được cộng thêm vào user đã tồn tại.
            if (explicitRoles != null && !explicitRoles.isEmpty()) {
                for (String r : explicitRoles) {
                    if (!user.getRoles().contains(r)) {
                        user.getRoles().add(r);
                    }
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
