package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.model.User;
import com.dungphd.insuranceass.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                String email = jwtTokenProvider.getEmailFromToken(jwt);
                // Lookup DB mỗi request (PLAN §1.7): tài khoản bị khóa thì token cũ
                // bị từ chối ngay; roles lấy MỚI NHẤT từ DB thay vì tin claims trong JWT
                // nên đổi role qua màn admin có hiệu lực ngay request tiếp theo.
                // Không phá luồng OAuth2/anonymous: filter này chỉ chạy ở chain JWT
                // chính; không có token hoặc không tìm thấy user thì đơn giản là
                // không set authentication, request sau đó do SecurityConfig xử lý.
                User user = email != null
                        ? userRepository.findByEmail(email.trim().toLowerCase()).orElse(null)
                        : null;
                if (user == null) {
                    log.warn("JWT hợp lệ nhưng không tìm thấy user: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                if (Boolean.FALSE.equals(user.getEnabled())) {
                    log.warn("Từ chối token của tài khoản đã khóa: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                // L4: roles CHỈ lấy từ DB, không fallback về claim JWT và không
                // mặc định ROLE_USER. User tồn tại nhưng roles rỗng/null trong DB
                // là trạng thái không hợp lệ → xử lý như tài khoản bị khóa: không
                // set authentication, để matcher phía sau chặn request.
                List<String> dbRoles = user.getRoles();
                if (dbRoles == null || dbRoles.isEmpty()) {
                    log.warn("Từ chối token: user không có vai trò trong DB: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = dbRoles.stream()
                        .map(SimpleGrantedAuthority::new).collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        email, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
