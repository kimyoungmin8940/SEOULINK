package com.seoulink.backend.oauth;

import com.seoulink.backend.dto.LoginResponseDto;
import com.seoulink.backend.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final MemberService memberService;

    public OAuth2LoginSuccessHandler(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User user = oauthToken.getPrincipal();
        String email = null;
        String name = null;

        if ("google".equals(registrationId)) {
            email = user.getAttribute("email");
            name = user.getAttribute("name");
        } else if ("kakao".equals(registrationId)) {
            Map<String, Object> account = (Map<String, Object>) user.getAttributes().get("kakao_account");
            Map<String, Object> properties = (Map<String, Object>) user.getAttributes().get("properties");
            if (account != null) email = (String) account.get("email");
            if (properties != null) name = (String) properties.get("nickname");
            if (name == null || name.isBlank()) name = "카카오회원";
        } else if ("naver".equals(registrationId)) {
            Map<String, Object> profile = (Map<String, Object>) user.getAttributes().get("response");
            if (profile != null) {
                email = (String) profile.get("email");
                name = (String) profile.get("name");
                if (name == null || name.isBlank()) name = (String) profile.get("nickname");
            }
            if (name == null || name.isBlank()) name = "네이버회원";
        }

        LoginResponseDto login = memberService.socialLogin(registrationId, email, name);
        String redirectUrl = "http://localhost:5173/oauth-success"
                + "?memberId=" + login.getMemberId()
                + "&email=" + encode(login.getEmail())
                + "&name=" + encode(login.getName())
                + "&loginType=" + encode(login.getLoginType());
        response.sendRedirect(redirectUrl);
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
