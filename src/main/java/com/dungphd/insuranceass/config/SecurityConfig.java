package com.dungphd.insuranceass.config;

import com.dungphd.insuranceass.config.ratelimit.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        config.setExposedHeaders(List.of(
                "Authorization",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After",
                "Content-Disposition"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Chain riêng cho OAuth2 login (Google): cần session để lưu authorization request,
     * trong khi chain chính là STATELESS cho JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler));
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(Customizer.withDefaults())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**", "/api/auth/**").permitAll()
                        // Đợt 1C §3(a): chỉ /actuator/health public (load balancer /
                        // uptime check); mọi actuator khác yêu cầu đăng nhập. Matcher
                        // health PHẢI đứng trước matcher actuator chung (match đầu thắng).
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/user/**", "/api/user/**").authenticated()
                        // Thứ tự quan trọng (match đầu tiên thắng): /excel trước /admin
                        // vì UNDERWRITER được vào excel nhưng không được vào /admin/**.
                        .requestMatchers("/excel/**", "/api/excel/**").hasAnyRole("ADMIN", "UNDERWRITER")
                        // Đợt 2A: hàng đợi thương lượng cho UNDERWRITER (+ ADMIN xem).
                        // Prefix riêng, không xung đột matcher khác; đứng trong nhóm
                        // matcher có role, trước anyRequest().authenticated() cuối.
                        .requestMatchers("/underwriter/**", "/api/underwriter/**").hasAnyRole("UNDERWRITER", "ADMIN")
                        // Đợt 3C: đối soát cho FINANCE (+ ADMIN xem). Prefix
                        // riêng, đứng trong nhóm matcher có role, trước
                        // anyRequest().authenticated() cuối.
                        .requestMatchers("/finance/**", "/api/finance/**").hasAnyRole("FINANCE", "ADMIN")
                        // Nối ở merge đợt 3 (tăng cường, service vẫn enforce lại):
                        // /agent/** chỉ AGENT + ADMIN (trước đây rơi vào anyRequest).
                        .requestMatchers("/agent/**", "/api/agent/**").hasAnyRole("AGENT", "ADMIN")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/benchmark/**", "/api/benchmark/**").hasRole("ADMIN")
                        // Đợt 1C §3(b): FINANCE chỉ được ĐỌC báo cáo. Matcher GET PHẢI
                        // đứng trước matcher chung (match đầu thắng); POST/PUT/DELETE
                        // trên /reports/** vẫn chỉ ADMIN (matcher chung phía dưới).
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/reports/**", "/api/reports/**").hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers("/reports/**", "/api/reports/**").hasRole("ADMIN")
                        // Khóa các endpoint nghiệp vụ: yêu cầu đăng nhập (đóng lỗ hổng 28 endpoints public)
                        // Frontend mọi trang đều qua ProtectedRoute nên không gãy luồng nào
                        // Đợt 4B: maker-checker endorsement — matcher CHỈ authenticated
                        // vì danh sách có scope theo role và approve/reject do service
                        // tự enforce (UW-only); chặn role ở matcher sẽ phá scope
                        // "ADMIN xem, FINANCE 403, customer/agent theo phạm vi".
                        .requestMatchers("/pending-endorsements/**", "/api/pending-endorsements/**").authenticated()
                        .requestMatchers("/policies/**", "/api/policies/**").authenticated()
                        .requestMatchers("/endorsements", "/api/endorsements").authenticated()
                        .requestMatchers("/history", "/api/history").authenticated()
                        .requestMatchers("/versions/**", "/api/versions/**").authenticated()
                        // Fallback cuối: authenticated thay vì permitAll (PLAN §1.4) —
                        // path mới quên khai báo sẽ không public mặc định.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitingFilter, org.springframework.security.web.authentication.logout.LogoutFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
