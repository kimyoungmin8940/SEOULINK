package com.seoulink.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;

/**
 * 애플리케이션의 HTTP 보안 규칙을 설정한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 코스 API는 로그인 기능이 완성되기 전까지 테스트할 수 있도록 공개한다.
     * 다른 요청은 기존처럼 인증이 필요하다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 로컬 API 테스트를 위해 코스 API의 CSRF 검사 제외
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/courses",
                                "/api/courses/**"
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        // 예외 처리 과정에서 다시 전달되는 /error 요청은 인증 없이 통과시킨다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 로그인 연동 전까지 코스와 내 코스 조회 API 테스트 허용
                        .requestMatchers(
                                "/error",
                                "/api/courses",
                                "/api/courses/**",
                                "/api/members/me/courses"
                        ).permitAll()
                        // 위 임시 허용 경로를 제외한 기존 API는 인증된 사용자만 접근한다.
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
