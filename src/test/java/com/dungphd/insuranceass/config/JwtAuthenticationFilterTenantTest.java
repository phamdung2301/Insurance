package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.context.TenantContext;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SaaS Phase 1: JwtAuthenticationFilter resolve tenant 1 lần/request từ User
 * đã load từ DB (không query thêm, không tin JWT claim).
 *
 * <p>Tenant chỉ quan sát được BÊN TRONG chuỗi filter downstream (nơi service
 * chạy); sau khi filter trả về, finally phải clear — test bắt cả hai vế bằng
 * cách ghi lại giá trị TenantContext tại thời điểm FilterChain chạy.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTenantTest {

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
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private User user(String email, String agentId, List<String> roles) {
        return User.builder()
                .id("u1")
                .email(email)
                .agentId(agentId)
                .roles(new ArrayList<>(roles))
                .enabled(true)
                .build();
    }

    /** Chạy filter, trả về tenant quan sát được bên trong FilterChain. */
    private String tenantSeenDownstream(User user) throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-123");
        when(jwtTokenProvider.validateToken("token-123")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("token-123")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        AtomicReference<String> seen = new AtomicReference<>("NOT_CALLED");
        doAnswer(inv -> {
            seen.set(TenantContext.getTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        return seen.get();
    }

    @Test
    void agentWithAgentId_resolvesToThatTenant() throws Exception {
        assertEquals("tenant-1", tenantSeenDownstream(
                user("agent@example.com", "tenant-1", List.of("ROLE_AGENT"))));
        // Finally của filter đã clear: request sau không thấy tenant cũ.
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void agentWithoutAgentId_resolvesToEmpty_notFailClosed() throws Exception {
        // Phase 1: agent chưa gán tenant KHÔNG bị fail-closed (hành vi Phase 2).
        assertNull(tenantSeenDownstream(
                user("agent@example.com", null, List.of("ROLE_AGENT"))));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void adminWithAgentId_bypassesToEmpty() throws Exception {
        assertNull(tenantSeenDownstream(
                user("admin@example.com", "tenant-1", List.of("ROLE_ADMIN", "ROLE_USER"))));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void underwriterWithAgentId_bypassesToEmpty() throws Exception {
        assertNull(tenantSeenDownstream(
                user("uw@example.com", "tenant-1", List.of("ROLE_UNDERWRITER"))));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void adminAlsoAgent_bypassesBecauseAdminWins() throws Exception {
        assertNull(tenantSeenDownstream(
                user("boss@example.com", "tenant-1", List.of("ROLE_ADMIN", "ROLE_AGENT"))));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void resolveTenantId_staticRules() {
        assertNull(JwtAuthenticationFilter.resolveTenantId(null));
        assertEquals("t9", JwtAuthenticationFilter.resolveTenantId(
                user("a@x.com", "t9", List.of("ROLE_AGENT"))));
        assertNull(JwtAuthenticationFilter.resolveTenantId(
                user("a@x.com", "  ", List.of("ROLE_AGENT"))));
    }
}
