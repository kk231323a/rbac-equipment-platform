package com.kk.rbac.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.dto.RefreshResponse;
import com.kk.rbac.security.UserAuthState;
import org.apache.ibatis.javassist.ClassPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final Duration REFRESH_MAX_LIFETIME = Duration.ofDays(30);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private UserAuthStateService userAuthStateService;
    @Autowired
    private JwtService jwtService;
    private static final DefaultRedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT;

    static {
        ROTATE_REFRESH_TOKEN_SCRIPT = new DefaultRedisScript<>();
        ROTATE_REFRESH_TOKEN_SCRIPT.setLocation(
                new ClassPathResource("script/rotateRefreshToken.lua")
        );
        ROTATE_REFRESH_TOKEN_SCRIPT.setResultType(Long.class);
    }

    private record RefreshTokenState(Integer userId, Long tokenVersion, String secretHash,long absExpiresAtMillis) {
    }

    public String createRefreshToken(String sid, Integer userId, Long tokenVersion) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String secret = Base64.getUrlEncoder()//base64编码，格式化随机数组
                .withoutPadding()//去除=占位
                .encodeToString(randomBytes);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("refreshKey".getBytes(), "HmacSHA256"));
            byte[] digest = mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
//            byte[] digest = MessageDigest.getInstance("SHA-256")
//                    .digest(secret.getBytes(StandardCharsets.UTF_8));//计算secret的哈希值
            String secretHash = HexFormat.of().formatHex(digest);//把算出的指纹转化成十六机制的字符串
            long absoluteExpiresAtMillis = Instant.now()
                    .plus(REFRESH_MAX_LIFETIME)
                    .toEpochMilli();
            String json = objectMapper.writeValueAsString(
                    new RefreshTokenState(userId, tokenVersion, secretHash,absoluteExpiresAtMillis)
            );

            Boolean saved = redisTemplate.opsForValue().setIfAbsent(
                    "auth:refresh:" + sid, json, REFRESH_TTL
            );
            if (!Boolean.TRUE.equals(saved)) {
                throw new BusinessException(503, "刷新凭证创建失败");
            }

            return sid + "." + secret;
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("刷新凭证生成失败", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
    public void revokeRefreshToken(String sid) {
        redisTemplate.delete("auth:refresh:" + sid);
    }

    public RefreshResponse refresh(String refreshToken) {
        // 1. 拆开凭证，查出并验证旧记录
        String[] parts = refreshToken == null
                ? new String[0]
                : refreshToken.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessException(401, "刷新凭证无效");
        }

        String sid = parts[0];
        String key = "auth:refresh:" + sid;

        try {
            String oldJson = redisTemplate.opsForValue().get(key);
            if (oldJson == null) {
                throw new BusinessException(401, "刷新凭证已失效");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("refreshKey".getBytes(), "HmacSHA256"));
            RefreshTokenState oldState = objectMapper.readValue(oldJson, RefreshTokenState.class);
            byte[] storedHash = HexFormat.of().parseHex(oldState.secretHash());
            byte[] submittedHash = mac.doFinal(parts[1].getBytes(StandardCharsets.UTF_8));
            //对再次加密后的secret和之前加密后并存在redis中的secretHash比较
            if (!MessageDigest.isEqual(submittedHash, storedHash)) {
                throw new BusinessException(401, "刷新凭证无效");
            }
            // 2. 检查账号当前状态，并算出这次最多能续到什么时候
            UserAuthState userState = userAuthStateService.findByUserId(oldState.userId());
            if (userState == null || !oldState.tokenVersion().equals(userState.tokenVersion())) {
                throw new BusinessException(401, "刷新凭证已失效");
            }
            if (userState.updating()) {
                throw new BusinessException(503, "用户认证状态正在更新，请稍后重试");
            }
            if (!userState.enabled()) {
                throw new BusinessException(401, "账号已被禁用");
            }

            long now = System.currentTimeMillis();
            long nextExpiresAt = Math.min(now + REFRESH_TTL.toMillis(), oldState.absExpiresAtMillis());
            if (nextExpiresAt <= now) {
                throw new BusinessException(401, "登录已到期，请重新登录");
            }

            // 3. 准备新凭证，等 Lua 写入成功后才返回
            byte[] randomBytes = new byte[32];
            RANDOM.nextBytes(randomBytes);
            String newSecret = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);

            byte[] newDigest = mac.doFinal(newSecret.getBytes(StandardCharsets.UTF_8));
            String newSecretHash = HexFormat.of().formatHex(newDigest);

            String newJson = objectMapper.writeValueAsString(new RefreshTokenState(
                    oldState.userId(),
                    oldState.tokenVersion(),
                    newSecretHash,
                    oldState.absExpiresAtMillis()
            ));

            String newAccessToken = jwtService.createToken(
                    oldState.userId(), oldState.tokenVersion(), sid
            );

            Long result = redisTemplate.execute(
                    ROTATE_REFRESH_TOKEN_SCRIPT, List.of(key),
                    oldJson, newJson, Long.toString(nextExpiresAt)
            );
            if (result == null) {
                throw new BusinessException(503, "刷新凭证更新未完成");
            }
            if (result != 1L) {
                throw new BusinessException(401, "刷新凭证已失效");
            }

            return new RefreshResponse(newAccessToken, sid + "." + newSecret);
        } catch (DataAccessException e) {
            throw new BusinessException(503, "认证服务暂不可用，请稍后重试");
        } catch (JsonProcessingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("刷新凭证处理失败", e);
        }
    }


}
