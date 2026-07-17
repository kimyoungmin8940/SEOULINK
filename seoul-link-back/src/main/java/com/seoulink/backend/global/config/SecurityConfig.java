package com.seoulink.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth를 사용하지 않는 로컬 개발 환경의 Security 설정이다.
 */
@Configuration
@Profile("!oauth")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // 현재 API 테스트를 위해 CSRF 검사 비활성화
                .csrf(csrf -> csrf.disable())

                // CorsConfig에 작성한 CORS 설정 적용
                .cors(Customizer.withDefaults())

                // 현재 개발 단계에서는 모든 요청 허용
                .authorizeHttpRequests(
                        auth -> auth
                                .anyRequest()
                                .permitAll()
                );

        return http.build();
    }
}