package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.mapper.SysUserMapper;
import com.kk.rbac.security.UserAuthState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthService {
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private UserAuthStateService userAuthStateService;
    @Autowired
    private PermissionService permissionService;

    public SysUser authenticate(String account, String rawPassword) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getAccount, account);
        SysUser user = sysUserMapper.selectOne(query);
        if (user == null) {
            throw new BusinessException(401, "账号或密码错误");
        }
        if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            if (!Boolean.TRUE.equals(user.getEnable())) {//Boolean.TRUE.equals(user.getEnable())可以避免空指针异常
                throw new BusinessException(403, "账号已被禁用");
            }
            return user;
        } else {
            throw new BusinessException(401, "账号或密码错误");
        }

    }

    public boolean isEnabled(Integer userId) {
        if (userId == null || userId <= 0)
            return false;
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null)
            return false;
        if (Boolean.TRUE.equals(user.getEnable()))
            return true;
        return false;
    }

    public UserAuthState prepareAuthState(SysUser user) {
        Integer userId = user.getId();
        UserAuthState state = userAuthStateService.findByUserId(userId);


        if (state == null) {
            UserAuthState initialState = new UserAuthState(
                    Boolean.TRUE.equals(user.getEnable()),
                    1L,
                    permissionService.getPermissionCodes(userId),
                    false
            );
            state = userAuthStateService.saveIfAbsent(userId, initialState)
                    ? initialState
                    : userAuthStateService.findByUserId(userId);
        }

        if (state == null || state.updating()) {
            throw new BusinessException(503, "用户认证状态暂不可用");
        }
        SysUser latestUser = sysUserMapper.selectById(userId);
        if (latestUser == null
                || !Objects.equals(user.getPasswordHash(), latestUser.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        if (!Boolean.TRUE.equals(latestUser.getEnable())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        return state;


    }

}
