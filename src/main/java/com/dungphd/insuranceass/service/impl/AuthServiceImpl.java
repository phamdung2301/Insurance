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
import com.dungphd.insuranceass.service.LoginHistoryService;
import com.dungphd.insuranceass.service.MailService;
import com.dungphd.insuranceass.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    /**
     * Ghi lịch sử đăng nhập (đợt 4D). Có thể null trong test cũ dùng
     * @InjectMocks mà không khai mock cho bean này — recordLogin() guard
     * null nên các test cũ vẫn chạy; test mới khai mock để verify hook.
     */
    private final LoginHistoryService loginHistoryService;

    @Value("${app.admin.emails:dungphdse@gmail.com,admin@insurtech.vn}")
    private String adminEmailsConfig;

    @Value("${app.dev.quick-switch-enabled:false}")
    private boolean devQuickSwitchEnabled;

    /**
     * Thông điệp DUY NHẤT cho mọi lần từ chối ở nhánh password khi tài khoản
     * không được phép đăng nhập do khóa (đợt 1A, L5): dùng chung cho cả khóa
     * tạm 15 phút do sai mật khẩu lẫn tài khoản bị admin disable, để không lộ
     * qua thông điệp rằng tài khoản rơi vào trường hợp nào. Lưu ý: các nhánh
     * OTP/dev/Google vẫn giữ thông điệp disable riêng của assertNotDisabled
     * (hành vi cũ, ngoài phạm vi đợt 1A) — coordinator quyết có thống nhất
     * toàn bộ hay không.
     */
    private static final String ACCOUNT_TEMPORARILY_LOCKED_MESSAGE =
            "Tài khoản tạm khóa, vui lòng thử lại sau.";

    @Override
    public void sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String otp = otpService.generateOtp(email);
        mailService.sendOtpEmail(email, otp);
        log.info("OTP dispatched for email: {}", email);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        return verifyOtp(request, "unknown", null);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request, String ip, String userAgent) {
        String email = request.getEmail().trim().toLowerCase();
        boolean valid = otpService.validateOtp(email, request.getOtp());
        if (!valid) {
            // OTP sai → KHÔNG ghi lịch sử (chỉ ghi khi thành công).
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn (300s). Vui lòng thử lại.");
        }

        assertNotDisabled(email);
        // OTP đúng = một lần xác thực thành công → mở khóa password (nếu có).
        otpService.clearPasswordLockout(email);
        User user = upsertUser(email, null, null);
        // Đường thành công 1/3 (OTP qua /auth/verify-otp): ghi SAU khi mọi
        // bước xác thực đã qua; recordLogin failure-isolated nên không thể
        // làm fail lần đăng nhập này.
        recordLogin(user.getEmail(), ip, userAgent);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(AuthLoginRequest request) {
        return login(request, "unknown", null);
    }

    @Override
    public AuthResponse login(AuthLoginRequest request, String ip, String userAgent) {
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

        // 2. Password login mode: tài khoản phải TỒN TẠI, mật khẩu khớp
        // BCrypt, KHÔNG tự tạo user và KHÔNG đổi role (khác dev/OTP path).
        // Sai email hay sai mật khẩu đều trả cùng 1 thông điệp để tránh
        // lộ tài khoản nào tồn tại (user enumeration).
        // Khóa password (đợt 1A, L5): 5 lần sai → khóa 15 phút (xem OtpService).
        // Đang khóa thì từ chối NGAY CẢ KHI ĐÚNG mật khẩu, trước khi check
        // mật khẩu; chỉ đếm lỗi khi tài khoản tồn tại (email lạ → chỉ trả
        // thông điệp chung, không tạo trạng thái khóa).
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Email is required for authentication");
            }
            if (otpService.isPasswordLocked(email)) {
                throw new ForbiddenException(ACCOUNT_TEMPORARILY_LOCKED_MESSAGE);
            }
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không đúng."));
            // Không dùng assertNotDisabled ở đây: thông điệp disable riêng của
            // nó sẽ lộ phân biệt "bị disable" vs "đang khóa tạm" — nhánh
            // password dùng chung thông điệp khóa tạm (xem hằng số trên).
            if (Boolean.FALSE.equals(user.getEnabled())) {
                throw new ForbiddenException(ACCOUNT_TEMPORARILY_LOCKED_MESSAGE);
            }
            if (user.getPassword() == null
                    || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                otpService.recordFailedPasswordAttempt(email);
                throw new BadRequestException("Email hoặc mật khẩu không đúng.");
            }
            // Đăng nhập đúng → reset bộ đếm lỗi (tránh 4 sai + 1 đúng + 1 sai
            // vẫn bị khóa oan do bộ đếm không reset).
            otpService.clearPasswordLockout(email);
            // Null-safe: document cũ có thể không có roles
            if (user.getRoles() == null) {
                user.setRoles(new ArrayList<>(List.of("ROLE_USER")));
            }
            // Đường thành công 2/3 (password). Sai mật khẩu đã throw ở trên
            // nên tới đây chắc chắn là thành công → mới ghi.
            recordLogin(user.getEmail(), ip, userAgent);
            return buildAuthResponse(user);
        }

        // 3. Regular OTP verification mode
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
        // OTP đúng qua login() = xác thực thành công → mở khóa password.
        otpService.clearPasswordLockout(email);
        User user = upsertUser(email, request.getFullName(), null);
        // Đường thành công 3a/3 (OTP qua /auth/login). Nhánh dev quick-switch
        // ở đầu method return riêng và KHÔNG gọi recordLogin — cố tình.
        recordLogin(user.getEmail(), ip, userAgent);
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse loginWithOAuth2(String email, String fullName) {
        return loginWithOAuth2(email, fullName, "unknown", null);
    }

    @Override
    public AuthResponse loginWithOAuth2(String email, String fullName, String ip, String userAgent) {
        String normalized = email != null ? email.trim().toLowerCase() : null;
        assertNotDisabled(normalized);
        // Google login thành công → mở khóa password (nếu có).
        otpService.clearPasswordLockout(normalized);
        // upsert với explicitRoles=null -> chỉ cấp ROLE_ADMIN lúc TẠO MỚI nếu email
        // nằm trong app.admin.emails (xem upsertUser); login sau không tự cộng lại.
        User user = upsertUser(normalized, fullName, null);
        // Đường thành công 3b/3 (Google). Tới đây OAuth2 đã xác thực xong
        // (handler chỉ gọi khi Google trả email hợp lệ) → mới ghi.
        recordLogin(user.getEmail(), ip, userAgent);
        return buildAuthResponse(user);
    }

    /**
     * Ghi lịch sử, failure-isolated 2 lớp: LoginHistoryServiceImpl đã
     * catch + log mọi lỗi lưu trữ; ở đây bọc thêm try/catch + guard null
     * (test cũ không có mock bean này) để lần đăng nhập không bao giờ
     * fail vì bước ghi lịch sử.
     */
    private void recordLogin(String email, String ip, String userAgent) {
        try {
            if (loginHistoryService != null) {
                loginHistoryService.recordSuccessfulLogin(email, ip, userAgent);
            }
        } catch (Exception e) {
            log.warn("Failed to record login history for {}: {}", email, e.getMessage());
        }
    }

    /** Tài khoản bị khóa → 403 ở mọi login path (OTP, dev, mật khẩu). */
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
