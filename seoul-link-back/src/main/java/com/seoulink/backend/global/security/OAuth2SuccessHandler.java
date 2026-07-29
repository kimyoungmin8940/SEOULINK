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
import java.util.Optional;
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
        String provider = token.getAuthorizedClientRegistrationId().toUpperCase();
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        SocialProfile profile = extractProfile(provider, oauthUser);
        if (profile.email() == null || profile.email().isBlank()
                || profile.socialId() == null || profile.socialId().isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth provider did not provide the required account information.");
            return;
        }

        Optional<Member> socialMember = memberRepository
                .findBySocialProviderAndSocialId(provider, profile.socialId());

        Member member;
        if (socialMember.isPresent()) {
            member = socialMember.get();
        } else if (memberRepository.findByEmail(profile.email()).isPresent()) {
            response.sendError(
                    HttpServletResponse.SC_CONFLICT,
                    "An account with this email already exists. Please use its original login method."
            );
            return;
        } else {
            member = createMember(profile, provider);
        }

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
        member.setLoginType("SOCIAL");
        member.setSocialProvider(provider);
        member.setSocialId(profile.socialId());
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
    private SocialProfile extractProfile(String provider, OAuth2User oauthUser) {
        Map<String, Object> attributes = oauthUser.getAttributes();
        if ("KAKAO".equals(provider)) {
            Map<String, Object> account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
            Map<String, Object> properties = (Map<String, Object>) attributes.getOrDefault("properties", Map.of());
            return new SocialProfile(
                    stringValue(account.get("email"), null),
                    stringValue(properties.get("nickname"), "Kakao user"),
                    stringValue(attributes.get("id"), null)
            );
        }
        if ("NAVER".equals(provider)) {
            Map<String, Object> naverResponse = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
            return new SocialProfile(
                    stringValue(naverResponse.get("email"), null),
                    stringValue(naverResponse.get("name"), stringValue(naverResponse.get("nickname"), "Naver user")),
                    stringValue(naverResponse.get("id"), null)
            );
        }
        return new SocialProfile(
                stringValue(attributes.get("email"), null),
                stringValue(attributes.get("name"), "Google user"),
                stringValue(attributes.get("sub"), oauthUser.getName())
        );
    }

    private String stringValue(Object value, String defaultValue) {
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    private record SocialProfile(String email, String name, String socialId) { }
}
