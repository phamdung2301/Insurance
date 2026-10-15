package com.dungphd.insuranceass.config.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private RateLimitingService rateLimitingService;

    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new RateLimitingFilter(rateLimitingService);
    }

    @Test
    void doFilter_WhenAllowed_AddsHeadersAndProceeds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/policies");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitingService.RateLimitResult allowed = RateLimitingService.RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(99)
                .limit(100)
                .retryAfterSeconds(0)
                .build();

        when(rateLimitingService.checkRateLimit(any())).thenReturn(allowed);

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertEquals("100", response.getHeader("X-RateLimit-Limit"));
        assertEquals("99", response.getHeader("X-RateLimit-Remaining"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_WhenExceeded_Returns429AndJsonError() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitingService.RateLimitResult blocked = RateLimitingService.RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(0)
                .limit(10)
                .retryAfterSeconds(45)
                .build();

        when(rateLimitingService.checkRateLimit(any())).thenReturn(blocked);

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("45", response.getHeader("Retry-After"));
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("45", response.getHeader("X-RateLimit-Reset"));
        assertTrue(response.getContentAsString().contains("45 giây"));
        assertTrue(response.getContentAsString().contains("\"success\":false"));

        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_ActuatorAndOptions_BypassesFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/policies");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(request, response, filterChain);

        verify(rateLimitingService, never()).checkRateLimit(any());
        verify(filterChain).doFilter(request, response);
    }
}
