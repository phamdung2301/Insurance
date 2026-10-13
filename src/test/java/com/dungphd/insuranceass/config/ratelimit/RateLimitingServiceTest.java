package com.dungphd.insuranceass.config.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService();
        ReflectionTestUtils.setField(rateLimitingService, "enabled", true);
        ReflectionTestUtils.setField(rateLimitingService, "authCapacity", 3L);
        ReflectionTestUtils.setField(rateLimitingService, "authDurationSeconds", 60L);
        ReflectionTestUtils.setField(rateLimitingService, "docsCapacity", 5L);
        ReflectionTestUtils.setField(rateLimitingService, "docsDurationSeconds", 60L);
        ReflectionTestUtils.setField(rateLimitingService, "generalCapacity", 10L);
        ReflectionTestUtils.setField(rateLimitingService, "generalDurationSeconds", 60L);
    }

    @Test
    void resolveTier_CategorizesCorrectly() {
        assertEquals(RateLimitingService.RequestTier.AUTH, rateLimitingService.resolveTier("/auth/login"));
        assertEquals(RateLimitingService.RequestTier.AUTH, rateLimitingService.resolveTier("/api/auth/send-otp"));
        assertEquals(RateLimitingService.RequestTier.DOCUMENTS, rateLimitingService.resolveTier("/policies/POL-1/documents/certificate"));
        assertEquals(RateLimitingService.RequestTier.GENERAL, rateLimitingService.resolveTier("/policies"));
        assertEquals(RateLimitingService.RequestTier.GENERAL, rateLimitingService.resolveTier("/user/profile"));
    }

    @Test
    void resolveClientIp_PrefersXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.setRemoteAddr("10.0.0.1");

        String ip = rateLimitingService.resolveClientIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void resolveClientIp_FallsBackToXRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.22");
        request.setRemoteAddr("10.0.0.1");

        String ip = rateLimitingService.resolveClientIp(request);
        assertEquals("198.51.100.22", ip);
    }

    @Test
    void resolveClientIp_FallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");

        String ip = rateLimitingService.resolveClientIp(request);
        assertEquals("192.168.1.50", ip);
    }

    @Test
    void checkRateLimit_ExceedingAuthCapacity_BlocksAndReturnsRetryAfter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");
        request.setRemoteAddr("192.168.1.100");

        // 3 calls should pass (capacity = 3)
        assertTrue(rateLimitingService.checkRateLimit(request).isAllowed());
        assertTrue(rateLimitingService.checkRateLimit(request).isAllowed());
        assertTrue(rateLimitingService.checkRateLimit(request).isAllowed());

        // 4th call should be blocked
        RateLimitingService.RateLimitResult blocked = rateLimitingService.checkRateLimit(request);
        assertFalse(blocked.isAllowed());
        assertEquals(0, blocked.getRemainingTokens());
        assertTrue(blocked.getRetryAfterSeconds() > 0);
    }

    @Test
    void checkRateLimit_WhenDisabled_AllowsAllRequests() {
        ReflectionTestUtils.setField(rateLimitingService, "enabled", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");
        request.setRemoteAddr("10.10.10.10");

        for (int i = 0; i < 20; i++) {
            assertTrue(rateLimitingService.checkRateLimit(request).isAllowed());
        }
    }
}
