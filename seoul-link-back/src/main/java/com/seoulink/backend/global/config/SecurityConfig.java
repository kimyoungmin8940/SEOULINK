package com.seoulink.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth 보안 설정을 사용하지 않는 환경의 기본 HTTP 보안 규칙을 설정한다.
 */
@Configuration
@EnableWebSecurity
@Profile("!oauth")
public class SecurityConfig {

    /**
     * 로그인·JWT 통합 전까지 장소 추천과 코스 API를 프론트에서 테스트할 수 있도록
     * 요청을 허용하고, REST API에 불필요한 기본 로그인 방식은 비활성화한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 현재 API는 세션 기반 폼 요청을 사용하지 않으므로 CSRF 검사를 비활성화한다.
                .csrf(csrf -> csrf.disable())
                // CorsConfig에 정의한 프론트엔드 허용 규칙을 Spring Security에도 적용한다.
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // 추천 후보 생성과 코스 최적화·저장·조회 흐름을 모두 테스트할 수 있다.
                        .requestMatchers(
                                "/error",
                                "/api/places/**",
                                "/api/courses",
                                "/api/courses/**",
                                "/api/members/me/courses"
                        ).permitAll()
                        // 인증 기능이 완성되기 전까지 나머지 API도 임시로 허용한다.
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
