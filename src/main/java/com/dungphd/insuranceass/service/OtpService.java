package com.dungphd.insuranceass.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALIDITY_SECONDS = 300; // 5 minutes TTL

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();

    private record OtpEntry(String code, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Generate a 6-digit numeric OTP with 300 seconds TTL
     */
    public String generateOtp(String email) {
        cleanExpiredOtps();

        String normalizedEmail = email.trim().toLowerCase();
        int number = secureRandom.nextInt(1_000_000);
        String code = String.format("%0" + OTP_LENGTH + "d", number);

        Instant expiresAt = Instant.now().plusSeconds(OTP_VALIDITY_SECONDS);
        otpCache.put(normalizedEmail, new OtpEntry(code, expiresAt));

        log.info("Generated OTP for email {}: {} (Expires in {}s)", normalizedEmail, code, OTP_VALIDITY_SECONDS);
        return code;
    }

    /**
     * Validate the provided OTP against the cached code
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

        boolean isValid = entry.code().equals(otp.trim());
        if (isValid) {
            otpCache.remove(normalizedEmail);
            log.info("OTP validated successfully for email {}", normalizedEmail);
        } else {
            log.warn("Invalid OTP entered for email {}", normalizedEmail);
        }

        return isValid;
    }

    public void clearOtp(String email) {
        if (email != null) {
            otpCache.remove(email.trim().toLowerCase());
        }
    }

    private void cleanExpiredOtps() {
        otpCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
