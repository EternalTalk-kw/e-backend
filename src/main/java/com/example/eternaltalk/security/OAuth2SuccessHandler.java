package com.example.eternaltalk.security;

import com.example.eternaltalk.domain.AuthProvider;
import com.example.eternaltalk.domain.Role;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.repository.UserRepository;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component @RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oUser = (OAuth2User) authentication.getPrincipal();
        String email = (String) oUser.getAttributes().get("email");
        String nickname = (String) oUser.getAttributes().getOrDefault("name", email);

        User user = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(email)
                        .passwordHash("{noop}")           // 구글 계정은 패스워드 사용안함
                        .nickname(nickname)
                        .authProvider(AuthProvider.GOOGLE)
                        .role(Role.USER)
                        .build())
        );

        String access = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refresh = tokenProvider.createRefreshToken(user.getId());

        // 프론트에 맞게 리다이렉트 URL 구성(예: 앱 커스텀 스키마)
        response.sendRedirect("/oauth2/success?access=" + access + "&refresh=" + refresh);
    }
}
