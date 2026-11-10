package com.kk.rbac.service;

import com.kk.rbac.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecretKey jwtSigningKey;
    private final JwtProperties properties;

    public String createToken(Integer userId) {//生成JWT token
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID必须为正数");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTtl());

        return Jwts.builder()
                .subject(userId.toString())//标识用户
                .id(UUID.randomUUID().toString())//标识token
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(jwtSigningKey, Jwts.SIG.HS256)
                .compact();
    }
    public Claims parseToken(String token) {//解析JWT token
        return Jwts.parser()
                .verifyWith(jwtSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}