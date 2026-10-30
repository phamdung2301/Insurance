package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Đợt 1B — L4: bỏ fallback token. User tồn tại trong DB nhưng roles rỗng/null
 * → không set authentication (như nhánh tài khoản bị khóa), không fallback về
 * claim JWT, không mặc định ROLE_USER.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(request.getHeader("Authorization")).thenReturn("Bearer token-123");
        when(jwtTokenProvider.validateToken("token-123")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("token-123")).thenReturn("user@example.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User userWithRoles(List<String> roles) {
        return User.builder()
                .id("u1")
                .email("user@example.com")
                .roles(roles == null ? null : new ArrayList<>(roles))
                .enabled(true)
                .build();
    }

    @Test
    void emptyDbRoles_NoAuthenticationAndNoJwtFallback() throws Exception {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userWithRoles(List.of())));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider, never()).getRolesFromToken(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nullDbRoles_NoAuthenticationAndNoJwtFallback() throws Exception {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userWithRoles(null)));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenProvider, never()).getRolesFromToken(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonEmptyDbRoles_UsesDbRolesAndIgnoresJwtClaims() throws Exception {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(userWithRoles(List.of("ROLE_AGENT"))));

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("user@example.com", auth.getPrincipal());
        assertEquals(1, auth.getAuthorities().size());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AGENT")));
        verify(jwtTokenProvider, never()).getRolesFromToken(any());
        verify(filterChain).doFilter(request, response);
    }
}
