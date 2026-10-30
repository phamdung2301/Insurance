package com.dungphd.insuranceass.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientRequestInfoTest {

    @Test
    void extractIp_ForwardedForMultipleValues_TakesFirstTrimmed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1, 172.16.0.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        assertEquals("203.0.113.7", ClientRequestInfo.extractIp(request));
    }

    @Test
    void extractIp_ForwardedForWithSpaces_TakesFirstTrimmed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("  198.51.100.9 , 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        assertEquals("198.51.100.9", ClientRequestInfo.extractIp(request));
    }

    @Test
    void extractIp_NoForwardedHeader_UsesRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.0.2.5");

        assertEquals("192.0.2.5", ClientRequestInfo.extractIp(request));
    }

    @Test
    void extractIp_BlankForwardedHeader_FallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("192.0.2.5");

        assertEquals("192.0.2.5", ClientRequestInfo.extractIp(request));
    }

    @Test
    void extractIp_NullRequest_ReturnsUnknown() {
        assertEquals("unknown", ClientRequestInfo.extractIp(null));
        assertNull(ClientRequestInfo.extractUserAgent(null));
    }

    @Test
    void extractUserAgent_ReturnsRawHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Chrome/130.0");

        assertEquals("Mozilla/5.0 Chrome/130.0", ClientRequestInfo.extractUserAgent(request));
    }

    @Test
    void extractUserAgent_MissingHeader_ReturnsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn(null);

        assertNull(ClientRequestInfo.extractUserAgent(request));
    }
}
