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
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final int EMAIL_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 50;
    private static final int NICKNAME_MAX_LENGTH = 50;
    private static final String OAUTH_EMAIL_DOMAIN = "@oauth.seoulink.local";

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
        String provider = token.getAuthorizedClientRegistrationId()
                .trim()
                .toUpperCase(Locale.ROOT);
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        SocialProfile extractedProfile = extractProfile(provider, oauthUser);
        if (isBlank(extractedProfile.socialId())) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "OAuth provider did not provide a user ID."
            );
            return;
        }

        String socialId = extractedProfile.socialId().trim();
        String email = resolveEmail(extractedProfile.email(), provider, socialId);
        String name = normalizeName(extractedProfile.name(), provider);
        SocialProfile profile = new SocialProfile(email, name, socialId);

        Member member = findOrCreateMember(profile, provider);
        if (!"ACTIVE".equals(member.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This account is inactive.");
            return;
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

    /**
     * 1) 동일 제공자 + 동일 소셜 ID가 있으면 기존 소셜 회원을 사용한다.
     * 2) 동일 이메일 회원이 있으면 409를 내지 않고 그 회원으로 로그인한다.
     *    기존 LOCAL 회원을 SOCIAL로 변경하면 DB 제약조건과 일반 로그인이 깨지므로
     *    회원 정보 자체는 변경하지 않는다.
     * 3) 어느 쪽도 없을 때만 신규 소셜 회원을 생성한다.
     */
    private Member findOrCreateMember(SocialProfile profile, String provider) {
        Optional<Member> socialMember = memberRepository
                .findBySocialProviderAndSocialId(provider, profile.socialId());
        if (socialMember.isPresent()) {
            return socialMember.get();
        }

        Optional<Member> emailMember = memberRepository.findByEmailIgnoreCase(profile.email());
        if (emailMember.isPresent()) {
            return emailMember.get();
        }

        return createMember(profile, provider);
    }

    private Member createMember(SocialProfile profile, String provider) {
        Member member = new Member();
        member.setEmail(profile.email());
        member.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        member.setName(profile.name());
        member.setNickname(createNickname(profile.name(), profile.email()));
        member.setLoginType("SOCIAL");
        member.setSocialProvider(provider);
        member.setSocialId(profile.socialId());
        member.setStatus("ACTIVE");
        return memberRepository.save(member);
    }

    private String createNickname(String name, String email) {
        String emailLocalPart = email.substring(0, email.indexOf('@'));
        String preferred = isBlank(name) ? emailLocalPart : name.trim();
        String base = sanitizeNickname(preferred);
        if (base.isBlank()) {
            base = sanitizeNickname(emailLocalPart);
        }
        if (base.isBlank()) {
            base = "seoulink";
        }
        base = truncate(base, NICKNAME_MAX_LENGTH);

        String candidate = base;
        int suffix = 1;
        while (memberRepository.existsByNickname(candidate)) {
            String suffixText = "_" + suffix++;
            candidate = truncate(base, NICKNAME_MAX_LENGTH - suffixText.length()) + suffixText;
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private SocialProfile extractProfile(String provider, OAuth2User oauthUser) {
        Map<String, Object> attributes = oauthUser.getAttributes();

        if ("KAKAO".equals(provider)) {
            Map<String, Object> account = mapValue(attributes.get("kakao_account"));
            Map<String, Object> accountProfile = mapValue(account.get("profile"));
            Map<String, Object> properties = mapValue(attributes.get("properties"));

            String nickname = firstNonBlank(
                    stringValue(accountProfile.get("nickname"), null),
                    stringValue(properties.get("nickname"), null),
                    "Kakao user"
            );

            return new SocialProfile(
                    stringValue(account.get("email"), null),
                    nickname,
                    // Kakao의 id는 문자열이 아니라 Long으로 내려오는 경우가 일반적이다.
                    stringValue(attributes.get("id"), oauthUser.getName())
            );
        }

        if ("NAVER".equals(provider)) {
            Map<String, Object> naverResponse = mapValue(attributes.get("response"));
            return new SocialProfile(
                    stringValue(naverResponse.get("email"), null),
                    firstNonBlank(
                            stringValue(naverResponse.get("name"), null),
                            stringValue(naverResponse.get("nickname"), null),
                            "Naver user"
                    ),
                    stringValue(naverResponse.get("id"), oauthUser.getName())
            );
        }

        return new SocialProfile(
                stringValue(attributes.get("email"), null),
                stringValue(attributes.get("name"), "Google user"),
                stringValue(attributes.get("sub"), oauthUser.getName())
        );
    }

    private String resolveEmail(String providerEmail, String provider, String socialId) {
        if (!isBlank(providerEmail)) {
            return truncate(providerEmail.trim().toLowerCase(Locale.ROOT), EMAIL_MAX_LENGTH);
        }

        // 카카오 등에서 이메일 동의를 받지 못해도 소셜 ID로 재로그인 가능한 내부 이메일을 만든다.
        String hash = UUID.nameUUIDFromBytes(
                        (provider + ":" + socialId).getBytes(StandardCharsets.UTF_8)
                )
                .toString()
                .replace("-", "");
        String localPart = provider.toLowerCase(Locale.ROOT) + "_" + hash;
        int maxLocalPartLength = EMAIL_MAX_LENGTH - OAUTH_EMAIL_DOMAIN.length();
        return truncate(localPart, maxLocalPartLength) + OAUTH_EMAIL_DOMAIN;
    }

    private String normalizeName(String name, String provider) {
        String value = isBlank(name) ? provider + " user" : name.trim();
        return truncate(value, NAME_MAX_LENGTH);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? defaultValue : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String sanitizeNickname(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[\\p{Cntrl}]", "");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SocialProfile(String email, String name, String socialId) { }
}
