package com.kk.rbac.service;

import com.kk.rbac.mapper.SysPermissionMapper;
import com.kk.rbac.mapper.SysUserMapper;
import com.kk.rbac.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionService {
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysPermissionMapper sysPermissionMapper;

    public List<String> getPermissionCodes(Integer userId) {
        return sysUserMapper.selectPermissionCodesByUserId(userId);
    }
    public List<PermissionVO> listAllPermissions(){
        return sysPermissionMapper.selectAllPermissions();
    }
}