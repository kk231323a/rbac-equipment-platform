package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SysUserMapper sysUserMapper;

    public SysUser authenticate(String account, String rawPassword){
        LambdaQueryWrapper<SysUser> query=new LambdaQueryWrapper<>();
        query.eq(SysUser::getAccount,account);
        SysUser user=sysUserMapper.selectOne(query);
        if(user==null){
            throw new BusinessException(401,"账号或密码错误");
        }
        if (passwordEncoder.matches(rawPassword,user.getPasswordHash())) {
            if (!Boolean.TRUE.equals(user.getEnable())) {//Boolean.TRUE.equals(user.getEnable())可以避免空指针异常
                throw new BusinessException(403,"账号已被禁用");
            }
            return user;
        } else {
            throw new BusinessException(401,"账号或密码错误");
        }

    }

}
