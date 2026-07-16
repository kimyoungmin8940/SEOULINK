package com.seoulink.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!oauth")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(Customizer.withDefaults())

                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers(
                                        "/api/places/**"
                                )
                                .permitAll()
                                .anyRequest()
                                .permitAll()
                );

        return http.build();
    }
}