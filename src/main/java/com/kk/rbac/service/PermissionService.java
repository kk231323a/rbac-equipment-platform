package com.kk.rbac.service;

import com.kk.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionService {
    @Autowired
    private SysUserMapper sysUserMapper;

    public List<String> getPermissionCodes(Integer userId) {
        return sysUserMapper.selectPermissionCodesByUserId(userId);
    }
}