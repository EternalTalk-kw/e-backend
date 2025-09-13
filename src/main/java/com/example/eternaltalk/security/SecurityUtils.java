package com.example.eternaltalk.security;

import com.example.eternaltalk.common.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static String currentUserEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return auth.getName(); // JwtAuthenticationFilter에서 email을 넣어줌
    }
}
