package com.seoulink.backend.config;

import com.seoulink.backend.oauth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Enabled only with SPRING_PROFILES_ACTIVE=oauth after provider keys are configured. */
@Configuration
@Profile("oauth")
public class OAuthSecurityConfig {
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    public OAuthSecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth.successHandler(oauth2LoginSuccessHandler));
        return http.build();
    }
}
