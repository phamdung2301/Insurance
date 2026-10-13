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
}
