package com.seoulink.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 애플리케이션의 HTTP 보안 규칙을 설정한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 코스 최적화 API는 로그인 기능이 완성되기 전까지 테스트할 수 있도록 공개한다.
     * 다른 요청은 기존처럼 인증이 필요하다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/courses/optimize")
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/optimize"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
