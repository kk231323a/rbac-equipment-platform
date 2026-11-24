package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysRolePermission;
import com.kk.rbac.mapper.SysPermissionMapper;
import com.kk.rbac.mapper.SysRoleMapper;
import com.kk.rbac.mapper.SysRolePermissionMapper;
import com.kk.rbac.vo.PermissionVO;
import com.kk.rbac.vo.RoleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysPermissionMapper sysPermissionMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public List<RoleVO> getAllRoles() {
        String json = redisTemplate.opsForValue().get("role");
        if (json!=null){
            try {
                return List.of(objectMapper.readValue(json, RoleVO.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return sysRoleMapper.selectAllRoles();
    }
    public List<PermissionVO> listRolePermissions(Integer roleId) {
        if (roleId == null || roleId <= 0) {
            throw new BusinessException(400, "角色ID不合法");
        }

        if (sysRoleMapper.selectById(roleId) == null) {
            throw new BusinessException(404, "角色不存在");
        }

        return sysRoleMapper.selectPermissionsByRoleId(roleId);
    }
    @AuditOperation(
            actionCode = AuditConstants.USER_PERMISSION_ASSIGN,
            resourceType = AuditConstants.ROLE,
            resourceId = "#roleId",
            operatorId = "#operatorId",
            independent = true,
            details = "{'roleId': #roleId, 'permissionId': #permissionId}"
    )
    public void assignPermission(Integer roleId, Integer permissionId, Integer operatorId) {
        if(roleId==null||roleId<=0||permissionId==null||permissionId<=0)
            throw new BusinessException(400,"参数错误");
        if(sysRoleMapper.selectById(roleId)==null)
            throw new BusinessException(404,"角色不存在");
        if(!sysPermissionMapper.existsById(permissionId))
            throw new BusinessException(404,"权限不存在");
        SysRolePermission rolePermission=new SysRolePermission();
        rolePermission.setPermissionId(permissionId);
        rolePermission.setRoleId(roleId);
        int rows;
        try {
            rows = sysRolePermissionMapper.insert(rolePermission);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "角色已拥有该权限");
        }

        if (rows != 1) {
            throw new BusinessException(500, "权限授予失败");
        }

    }
    @AuditOperation(
            actionCode = AuditConstants.USER_PERMISSION_REVOKE,
            resourceType = AuditConstants.ROLE,
            resourceId = "#roleId",
            operatorId = "#operatorId",
            independent = true,
            details = "{'roleId': #roleId, 'permissionId': #permissionId}"
    )
    public void revokePermission(Integer roleId, Integer permissionId, Integer operatorId){
        if(roleId==null||roleId<=0||permissionId==null||permissionId<=0)
            throw new BusinessException(400,"参数错误");
        if(sysRoleMapper.selectById(roleId)==null)
            throw new BusinessException(404,"角色不存在");
        if(!sysPermissionMapper.existsById(permissionId))
            throw new BusinessException(404,"权限不存在");
        LambdaQueryWrapper<SysRolePermission> condition =
                new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId)
                        .eq(SysRolePermission::getPermissionId, permissionId);

        int rows = sysRolePermissionMapper.delete(condition);

        if (rows != 1) {
            throw new BusinessException(409, "角色未拥有该权限或已被撤销");
        }
    }
}
