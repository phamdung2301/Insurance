package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OtpServiceSecurityTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
    }

    @Test
    void validateOtp_CorrectCode_ReturnsTrueAndClearsOtp() {
        String email = "customer@example.com";
        String otp = otpService.generateOtp(email);

        assertTrue(otpService.validateOtp(email, otp));
        // Once validated, subsequent attempt should return false (one-time use)
        assertFalse(otpService.validateOtp(email, otp));
    }

    @Test
    void validateOtp_Failed5Times_LocksOutAndThrowsBadRequestException() {
        String email = "bruteforce@example.com";
        String otp = otpService.generateOtp(email);
        String wrongOtp = "999999".equals(otp) ? "888888" : "999999";

        // Attempts 1 to 4 should return false
        assertFalse(otpService.validateOtp(email, wrongOtp));
        assertFalse(otpService.validateOtp(email, wrongOtp));
        assertFalse(otpService.validateOtp(email, wrongOtp));
        assertFalse(otpService.validateOtp(email, wrongOtp));

        // 5th attempt triggers security lockout
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.validateOtp(email, wrongOtp));

        assertTrue(ex.getMessage().contains("quá 5 lần"));

        // Subsequent attempt with even the CORRECT OTP is rejected because code was invalidated
        assertFalse(otpService.validateOtp(email, otp));
    }

    @Test
    void generateOtp_CalledWithin60SecondsCooldown_ThrowsBadRequestException() {
        String email = "spam@example.com";
        otpService.generateOtp(email);

        // Immediate subsequent request must trigger cooldown restriction
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.generateOtp(email));

        assertTrue(ex.getMessage().contains("giây trước khi yêu cầu"));
    }

    @Test
    void clearCooldown_AllowsGeneratingOtpImmediately() {
        String email = "cooldown@example.com";
        otpService.generateOtp(email);
        otpService.clearCooldown(email);

        assertDoesNotThrow(() -> otpService.generateOtp(email));
    }

    // ---- Đợt 1A, L5: khóa password login (cơ chế riêng, không phải OTP cache) ----

    @Test
    void passwordLockout_FewerThan5Attempts_NotLocked() {
        String email = "pwd-attempts@example.com";
        for (int i = 0; i < 4; i++) {
            otpService.recordFailedPasswordAttempt(email);
        }
        assertFalse(otpService.isPasswordLocked(email));
    }

    @Test
    void passwordLockout_After5FailedAttempts_IsLocked() {
        String email = "pwd-locked@example.com";
        for (int i = 0; i < 4; i++) {
            otpService.recordFailedPasswordAttempt(email);
            assertFalse(otpService.isPasswordLocked(email));
        }
        otpService.recordFailedPasswordAttempt(email);
        assertTrue(otpService.isPasswordLocked(email));
    }

    @Test
    void passwordLockout_ClearUnlocksAndResetsCounter() {
        String email = "pwd-unlock@example.com";
        for (int i = 0; i < 5; i++) {
            otpService.recordFailedPasswordAttempt(email);
        }
        assertTrue(otpService.isPasswordLocked(email));

        otpService.clearPasswordLockout(email);
        assertFalse(otpService.isPasswordLocked(email));

        // Bộ đếm đã reset: 4 lần sai nữa vẫn chưa khóa, lần thứ 5 mới khóa lại.
        for (int i = 0; i < 4; i++) {
            otpService.recordFailedPasswordAttempt(email);
        }
        assertFalse(otpService.isPasswordLocked(email));
        otpService.recordFailedPasswordAttempt(email);
        assertTrue(otpService.isPasswordLocked(email));
    }

    @Test
    void passwordLockout_NullEmail_IsSafe() {
        assertFalse(otpService.isPasswordLocked(null));
        assertDoesNotThrow(() -> otpService.recordFailedPasswordAttempt(null));
        assertDoesNotThrow(() -> otpService.clearPasswordLockout(null));
    }

    @Test
    void passwordLockout_IsIndependentFromOtpCache() {
        // Sai password 5 lần không làm hỏng OTP đang chờ và ngược lại.
        String email = "pwd-otp-independent@example.com";
        String otp = otpService.generateOtp(email);
        for (int i = 0; i < 5; i++) {
            otpService.recordFailedPasswordAttempt(email);
        }
        assertTrue(otpService.isPasswordLocked(email));
        assertTrue(otpService.validateOtp(email, otp));
    }
}
