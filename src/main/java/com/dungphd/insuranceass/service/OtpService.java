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

    private void cleanExpiredOtps() {
        otpCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        cooldownCache.entrySet().removeIf(entry -> Instant.now().isAfter(entry.getValue()));
    }
}
