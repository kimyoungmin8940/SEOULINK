package com.seoulink.backend.global.security;

import com.seoulink.backend.domain.member.entity.Member;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;
    private final String frontUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public OAuth2SuccessHandler(
            MemberRepository memberRepository,
            @Value("${app.front-url}") String frontUrl
    ) {
        this.memberRepository = memberRepository;
        this.frontUrl = frontUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        SocialProfile profile = extractProfile(provider, oauthUser.getAttributes());
        if (profile.email() == null || profile.email().isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth provider did not provide an email address.");
            return;
        }

        Member member = memberRepository.findByEmail(profile.email())
                .orElseGet(() -> createMember(profile, provider));

        String targetUrl = UriComponentsBuilder.fromUriString(frontUrl)
                .path("/oauth-success")
                .queryParam("memberId", member.getMemberId())
                .queryParam("email", member.getEmail())
                .queryParam("name", member.getName())
                .queryParam("loginType", member.getLoginType())
                .build()
                .encode()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private Member createMember(SocialProfile profile, String provider) {
        Member member = new Member();
        member.setEmail(profile.email());
        member.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        member.setName(profile.name());
        member.setNickname(createNickname(profile.email()));
        member.setLoginType(provider.toUpperCase());
        member.setStatus("ACTIVE");
        return memberRepository.save(member);
    }

    private String createNickname(String email) {
        String base = email.substring(0, Math.min(email.indexOf('@') < 1 ? email.length() : email.indexOf('@'), 40));
        String candidate = base;
        int suffix = 1;
        while (memberRepository.existsByNickname(candidate)) {
            candidate = base.substring(0, Math.min(base.length(), 40 - String.valueOf(suffix).length() - 1)) + "_" + suffix++;
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private SocialProfile extractProfile(String provider, Map<String, Object> attributes) {
        if ("kakao".equals(provider)) {
            Map<String, Object> account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
            Map<String, Object> properties = (Map<String, Object>) attributes.getOrDefault("properties", Map.of());
            return new SocialProfile((String) account.get("email"), stringValue(properties.get("nickname"), "Kakao user"));
        }
        if ("naver".equals(provider)) {
            Map<String, Object> response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
            return new SocialProfile((String) response.get("email"), stringValue(response.get("name"), stringValue(response.get("nickname"), "Naver user")));
        }
        return new SocialProfile((String) attributes.get("email"), stringValue(attributes.get("name"), "Google user"));
    }

    private String stringValue(Object value, String defaultValue) {
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    private record SocialProfile(String email, String name) { }
}
