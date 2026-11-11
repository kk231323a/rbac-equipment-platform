package com.kk.rbac.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenRevocationService {

    private static final String KEY_PREFIX = "auth:revoked:";
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void revoke(String jti, Instant expiresAt) {//撤销Token
        if (jti == null || jti.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("jti和过期时间不能为空");
        }

        long remainingMillis =
                expiresAt.toEpochMilli() - System.currentTimeMillis();

        if (remainingMillis <= 0) {
            return;
        }

        redisTemplate.opsForValue().set(
                KEY_PREFIX + jti,
                "1",
                Duration.ofMillis(remainingMillis)
        );
    }
    public boolean isRevoked(String jti) {//检查Token是否被撤销
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti不能为空");
        }

        Boolean revoked = redisTemplate.hasKey(KEY_PREFIX + jti);

        if (revoked == null) {
            throw new IllegalStateException("无法确认Token撤销状态");
        }

        return revoked;
    }
}