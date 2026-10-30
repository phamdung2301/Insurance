package com.dungphd.insuranceass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder sống ở @Configuration riêng, KHÔNG PHỤ THUỘC vào chuỗi
 * security — vì bean này từng nằm trong SecurityConfig mà SecurityConfig
 * lại phụ thuộc (qua OAuth2LoginSuccessHandler) vào AuthServiceImpl, và
 * AuthServiceImpl thì inject PasswordEncoder → vòng tròn tham chiếu lúc
 * boot (BeanCurrentlyInCreationException), chỉ lộ khi chạy thật, compile
 * và Mockito test đều không thấy.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
