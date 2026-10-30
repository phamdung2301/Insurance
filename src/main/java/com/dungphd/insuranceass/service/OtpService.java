package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALIDITY_SECONDS = 300; // 5 minutes TTL
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS = 60; // 60s resend cooldown

    /**
     * Khóa đăng nhập bằng mật khẩu (đợt 1A, L5). ĐÍNH CHÍNH so với giả định
     * trong brief: OtpService trước đợt này KHÔNG có khóa tài khoản 15 phút
     * nào cả; cơ chế sẵn có chỉ là: sai OTP 5 lần → mã OTP đó bị vô hiệu
     * hoá (tài khoản không bị khóa, không có đồng hồ 15 phút). Vì vậy đợt 1A bổ sung tại đây
     * một cơ chế khóa RIÊNG cho nhánh password, cùng ngưỡng 5 lần sai và
     * thời gian khóa 15 phút như yêu cầu đợt 1A, tách khỏi OTP cache (OTP
     * cache chỉ tồn tại khi đã generate OTP, còn password login thì không).
     * Khóa này CHỈ chặn nhánh password: OTP/Google login thành công vẫn đi
     * qua được và tự mở khóa (xem AuthServiceImpl).
     */
    private static final long PASSWORD_LOCKOUT_SECONDS = 900; // 15 minutes
    private final Map<String, AtomicInteger> passwordFailedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> passwordLockedUntil = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldownCache = new ConcurrentHashMap<>();

    private record OtpEntry(String code, Instant expiresAt, AtomicInteger failedAttempts) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Generate a 6-digit numeric OTP with 300 seconds TTL and 60 seconds resend cooldown
     */
    public String generateOtp(String email) {
        cleanExpiredOtps();

        String normalizedEmail = email.trim().toLowerCase();

        // Check resend cooldown
        Instant cooldownUntil = cooldownCache.get(normalizedEmail);
        if (cooldownUntil != null && Instant.now().isBefore(cooldownUntil)) {
            long remainingSeconds = cooldownUntil.getEpochSecond() - Instant.now().getEpochSecond();
            throw new BadRequestException("Vui lòng đợi " + Math.max(1, remainingSeconds) + " giây trước khi yêu cầu gửi lại mã OTP mới.");
        }

        int number = secureRandom.nextInt(1_000_000);
        String code = String.format("%0" + OTP_LENGTH + "d", number);

        Instant expiresAt = Instant.now().plusSeconds(OTP_VALIDITY_SECONDS);
        otpCache.put(normalizedEmail, new OtpEntry(code, expiresAt, new AtomicInteger(0)));
        cooldownCache.put(normalizedEmail, Instant.now().plusSeconds(COOLDOWN_SECONDS));

        log.info("Generated OTP for email {} (Expires in {}s)", normalizedEmail, OTP_VALIDITY_SECONDS);
        return code;
    }

    /**
     * Validate the provided OTP against the cached code using constant-time comparison
     * and max 5 attempts lockout.
     */
    public boolean validateOtp(String email, String otp) {
        if (email == null || otp == null) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        OtpEntry entry = otpCache.get(normalizedEmail);

        if (entry == null) {
            log.warn("No OTP found for email {}", normalizedEmail);
            return false;
        }

        if (entry.isExpired()) {
            otpCache.remove(normalizedEmail);
            log.warn("OTP for email {} has expired", normalizedEmail);
            return false;
        }

        // Constant-time byte comparison against timing attacks
        byte[] expected = entry.code().getBytes(StandardCharsets.UTF_8);
        byte[] provided = otp.trim().getBytes(StandardCharsets.UTF_8);
        boolean isValid = MessageDigest.isEqual(expected, provided);

        if (isValid) {
            otpCache.remove(normalizedEmail);
            cooldownCache.remove(normalizedEmail);
            log.info("OTP validated successfully for email {}", normalizedEmail);
            return true;
        }

        // Brute-force protection: track failed attempts
        int attempts = entry.failedAttempts().incrementAndGet();
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            otpCache.remove(normalizedEmail);
            log.warn("[SECURITY LOCKOUT] OTP for email {} invalidated after {} failed attempts", normalizedEmail, attempts);
            throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị vô hiệu hoá vì lý do bảo mật. Vui lòng yêu cầu mã mới.");
        }

        log.warn("Invalid OTP entered for email {} (Attempt {}/{})", normalizedEmail, attempts, MAX_FAILED_ATTEMPTS);
        return false;
    }

    public void clearOtp(String email) {
        if (email != null) {
            String normalized = email.trim().toLowerCase();
            otpCache.remove(normalized);
            cooldownCache.remove(normalized);
        }
    }

    public void clearCooldown(String email) {
        if (email != null) {
            cooldownCache.remove(email.trim().toLowerCase());
        }
    }

    /**
     * Nhánh password có đang bị khóa không. Khóa hết hạn → tự dọn và trả false
     * (không cần job dọn riêng; lần login tiếp theo sau 15 phút được thử lại).
     */
    public boolean isPasswordLocked(String email) {
        if (email == null) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        Instant until = passwordLockedUntil.get(normalized);
        if (until == null) {
            return false;
        }
        if (Instant.now().isBefore(until)) {
            return true;
        }
        // Hết hạn: dọn cả khóa lẫn bộ đếm để lần sai tiếp theo đếm lại từ 1.
        passwordLockedUntil.remove(normalized);
        passwordFailedAttempts.remove(normalized);
        return false;
    }

    /**
     * Ghi 1 lần sai mật khẩu. Đủ 5 lần → khóa 15 phút, xóa bộ đếm (để sau
     * khi hết khóa, lần sai tiếp theo đếm lại từ 1 thay vì khóa ngay lại).
     * Chỉ gọi khi tài khoản TỒN TẠI (AuthServiceImpl): không tạo trạng thái
     * khóa cho email lạ, tránh rò rỉ/khóa nhầm.
     */
    public void recordFailedPasswordAttempt(String email) {
        if (email == null) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (isPasswordLocked(normalized)) {
            return;
        }
        int attempts = passwordFailedAttempts
                .computeIfAbsent(normalized, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            passwordFailedAttempts.remove(normalized);
            passwordLockedUntil.put(normalized, Instant.now().plusSeconds(PASSWORD_LOCKOUT_SECONDS));
            log.warn("[SECURITY LOCKOUT] Password login for email {} locked for {}s after {} failed attempts",
                    normalized, PASSWORD_LOCKOUT_SECONDS, attempts);
        } else {
            log.warn("Invalid password entered for email {} (Attempt {}/{})", normalized, attempts, MAX_FAILED_ATTEMPTS);
        }
    }

    /**
     * Mở khóa password: đăng nhập đúng bằng mật khẩu (reset bộ đếm), đăng
     * nhập OTP/Google thành công, hoặc admin mở (hook admin do coordinator
     * nối sau vào AdminUserService — file đó ngoài phạm vi đợt 1A).
     */
    public void clearPasswordLockout(String email) {
        if (email != null) {
            String normalized = email.trim().toLowerCase();
            passwordFailedAttempts.remove(normalized);
            passwordLockedUntil.remove(normalized);
        }
    }

    private void cleanExpiredOtps() {
        otpCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        cooldownCache.entrySet().removeIf(entry -> Instant.now().isAfter(entry.getValue()));
    }
}
