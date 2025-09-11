package com.example.eternaltalk.service;

import com.example.eternaltalk.domain.AuthProvider;
import com.example.eternaltalk.domain.Role;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.dto.AuthDtos.*;
import com.example.eternaltalk.repository.UserRepository;
import com.example.eternaltalk.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public void signup(SignupRequest req){
        if (userRepository.existsByEmail(req.email())) throw new IllegalArgumentException("이미 가입된 이메일");
        User user = User.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
        userRepository.save(user);
    }

    public JwtResponse login(LoginRequest req){
        User u = userRepository.findByEmail(req.email()).orElseThrow(() -> new IllegalArgumentException("아이디/비밀번호 오류"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash()))
            throw new IllegalArgumentException("아이디/비밀번호 오류");

        String access = tokenProvider.createAccessToken(u.getId(), u.getEmail(), u.getRole());
        String refresh = tokenProvider.createRefreshToken(u.getId());
        return new JwtResponse(access, refresh);
    }
}
