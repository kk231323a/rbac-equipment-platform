package com.kk.rbac.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.security.UserAuthState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserAuthStateService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PermissionService permissionService;

    private static final DefaultRedisScript<Long> INCRJWTVERSION_SCRIPT;

    static {
        INCRJWTVERSION_SCRIPT = new DefaultRedisScript<>();
        INCRJWTVERSION_SCRIPT.setLocation(new ClassPathResource("script/simpleCAS.lua"));
        INCRJWTVERSION_SCRIPT.setResultType(Long.class);
    }

    public UserAuthState findByUserId(Integer userId) {
        String json = redisTemplate.opsForValue().get("auth:user:" + userId);
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户认证状态格式错误", exception);
        }
    }
    public UserAuthState findForToken(Integer userId, String jti,String sid) {
        List<String> keys = new ArrayList<>(List.of(
                "auth:user:" + userId,
                TokenRevocationService.KEY_PREFIX + jti
        ));

        if (sid != null) {
            keys.add("auth:refresh:" + sid);
        }
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null || values.size() != keys.size()) {
            throw new IllegalStateException("无法读取 Token 认证状态");
        }

        String stateJson = values.get(0);
        String revoked = values.get(1);
        boolean sessionMissing = sid != null && values.get(2) == null;

        if (sessionMissing||stateJson == null || revoked != null) {
            return null;
        }

        try {
            return objectMapper.readValue(stateJson, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("用户认证状态格式错误", e);
        }
    }

    public boolean saveIfAbsent(Integer userId, UserAuthState state) {
        String json = null;
        try {
            json = objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return redisTemplate.opsForValue().setIfAbsent("auth:user:" + userId, json);
    }

    public void markUpdating(SysUser user) {
        Integer userId = user.getId();
        String key = "auth:user:" + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null)//插入redis防止同时修改
        {
            UserAuthState initialState = new UserAuthState(
                    Boolean.TRUE.equals(user.getEnable()),
                    1L,
                    permissionService.getPermissionCodes(userId),
                    true
            );

            String markedJson;
            try {
                markedJson = objectMapper.writeValueAsString(initialState);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("用户认证状态序列化失败", e);
            }

            Boolean inserted = redisTemplate.opsForValue().setIfAbsent(key, markedJson);
            if (!Boolean.TRUE.equals(inserted)) {
                throw new BusinessException(503, "用户认证状态标记失败，请稍后重试");
            }
            return;
        }
        UserAuthState state = null;
        try {
            state = objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (state.updating()) {
            throw new BusinessException(409, "账号认证状态正在更新，请稍后重试");
        }
        UserAuthState updatingState = new UserAuthState(
                state.enabled(),
                state.tokenVersion(),
                state.permissions(),
                true
        );
        String newState = null;
        try {
            newState = objectMapper.writeValueAsString(updatingState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long result = redisTemplate.execute(INCRJWTVERSION_SCRIPT, List.of("auth:user:" + userId), json, newState);
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(503, "用户认证状态更新未完成");
        }


    }

    public void markNotUpdating(Integer userId) {
        String json = redisTemplate.opsForValue().get("auth:user:" + userId);
        if (json == null)
            throw new BusinessException(503,"状态更新失败");//判断为null防止进入下面反序列化操作报异常
        UserAuthState state = null;
        try {
            state = objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (!state.updating())
            throw new BusinessException(503,"状态更新失败");
        UserAuthState updatingState = new UserAuthState(
                state.enabled(),
                state.tokenVersion(),
                state.permissions(),
                false
        );
        String newState = null;
        try {
            newState = objectMapper.writeValueAsString(updatingState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long result = redisTemplate.execute(INCRJWTVERSION_SCRIPT, List.of("auth:user:" + userId), json, newState);
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(503, "用户认证状态更新未完成");
        }

    }

    public void incrJwtVersion(Integer userId) {
        String json = redisTemplate.opsForValue().get("auth:user:" + userId);
        if (json == null) throw new BusinessException(503,"状态更新失败");//判断为null防止进入下面反序列化操作报异常

        UserAuthState state = null;
        try {
            state = objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        UserAuthState newState = new UserAuthState(state.enabled(), state.tokenVersion() + 1, state.permissions(), false);
        String newJson = null;
        try {
            newJson = objectMapper.writeValueAsString(newState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long result = redisTemplate.execute(INCRJWTVERSION_SCRIPT, List.of("auth:user:" + userId), json, newJson);
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(503, "用户认证状态更新未完成");
        }
    }
    public void finishEnabledUpdate(Integer userId, boolean enabled){
        String json = redisTemplate.opsForValue().get("auth:user:" + userId);
        if (json == null) throw new BusinessException(503,"状态更新失败");//判断为null防止进入下面反序列化操作报异常

        UserAuthState state = null;
        try {
            state = objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        UserAuthState newState = new UserAuthState(enabled, state.tokenVersion() , state.permissions(), false);
        String newJson = null;
        try {
            newJson = objectMapper.writeValueAsString(newState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long result = redisTemplate.execute(INCRJWTVERSION_SCRIPT, List.of("auth:user:" + userId), json, newJson);
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(503, "用户启停状态更新未完成");
        }
    }
    public void finishPermissionsUpdate(Integer userId){
        String json = redisTemplate.opsForValue().get("auth:user:" + userId);
        if (json == null) throw new BusinessException(503,"状态更新失败");//判断为null防止进入下面反序列化操作报异常

        UserAuthState state = null;
        try {
            state = objectMapper.readValue(json, UserAuthState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        UserAuthState newState = new UserAuthState(
                state.enabled(),
                state.tokenVersion(),
                permissionService.getPermissionCodes(userId),
                true
        );
        String newJson = null;
        try {
            newJson = objectMapper.writeValueAsString(newState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Long result = redisTemplate.execute(INCRJWTVERSION_SCRIPT, List.of("auth:user:" + userId), json, newJson);
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(503, "用户权限更新未完成");
        }
    }
}
