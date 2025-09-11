package com.example.eternaltalk.security;

import com.example.eternaltalk.domain.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-expiration-ms}") long accessExpMs,
            @Value("${security.jwt.refresh-expiration-ms}") long refreshExpMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpMs = accessExpMs;
        this.refreshExpMs = refreshExpMs;
    }

    public String createAccessToken(Long userId, String email, Role role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    public String createRefreshToken(Long userId){
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    public Jws<Claims> parse(String token){ return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token); }
}
