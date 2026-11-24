package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysRolePermission;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.mapper.SysPermissionMapper;
import com.kk.rbac.mapper.SysRoleMapper;
import com.kk.rbac.mapper.SysRolePermissionMapper;
import com.kk.rbac.mapper.SysUserMapper;
import com.kk.rbac.vo.PermissionVO;
import com.kk.rbac.vo.RoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
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
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private UserAuthStateService userAuthStateService;

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
        List<Integer> markedUserIds = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            if (sysRoleMapper.selectIdForUpdate(roleId) == null) {
                throw new BusinessException(404, "角色不存在");
            }
            if (!sysPermissionMapper.existsById(permissionId)) {
                throw new BusinessException(404, "权限不存在");
            }

            registerPermissionRollback(roleId, markedUserIds);
            for (SysUser user : sysUserMapper.selectUsersByRoleId(roleId)) {
                userAuthStateService.markUpdating(user);
                markedUserIds.add(user.getId());
            }

            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);

            int rows;
            try {
                rows = sysRolePermissionMapper.insert(rolePermission);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(409, "角色已拥有该权限");
            }

            if (rows != 1) {
                throw new BusinessException(500, "权限授予失败");
            }
        });
        refreshMarkedPermissions(roleId, markedUserIds);
    }
    @AuditOperation(
            actionCode = AuditConstants.USER_PERMISSION_REVOKE,
            resourceType = AuditConstants.ROLE,
            resourceId = "#roleId",
            operatorId = "#operatorId",
            independent = true,
            details = "{'roleId': #roleId, 'permissionId': #permissionId}"
    )
    public void revokePermission(Integer roleId, Integer permissionId, Integer operatorId) {
        if (roleId == null || roleId <= 0 || permissionId == null || permissionId <= 0) {
            throw new BusinessException(400, "参数错误");
        }
        List<Integer> markedUserIds = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            if (sysRoleMapper.selectIdForUpdate(roleId) == null) {
                throw new BusinessException(404, "角色不存在");
            }
            if (!sysPermissionMapper.existsById(permissionId)) {
                throw new BusinessException(404, "权限不存在");
            }

            registerPermissionRollback(roleId, markedUserIds);
            for (SysUser user : sysUserMapper.selectUsersByRoleId(roleId)) {
                userAuthStateService.markUpdating(user);
                markedUserIds.add(user.getId());
            }

            LambdaQueryWrapper<SysRolePermission> condition =
                    new LambdaQueryWrapper<SysRolePermission>()
                            .eq(SysRolePermission::getRoleId, roleId)
                            .eq(SysRolePermission::getPermissionId, permissionId);

            int rows = sysRolePermissionMapper.delete(condition);
            if (rows != 1) {
                throw new BusinessException(409, "角色未拥有该权限或已被撤销");
            }
        });
        refreshMarkedPermissions(roleId, markedUserIds);
    }

    private void registerPermissionRollback(Integer roleId, List<Integer> markedUserIds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }

                for (Integer userId : markedUserIds) {
                    try {
                        userAuthStateService.markNotUpdating(userId);
                    } catch (RuntimeException exception) {
                        log.warn("角色权限变更回滚后解除用户更新标记失败，roleId={}，userId={}",
                                roleId, userId, exception);
                    }
                }
            }
        });
    }

    private void refreshMarkedPermissions(Integer roleId, List<Integer> markedUserIds) {
        boolean syncFailed = false;
        for (Integer userId : markedUserIds) {
            try {
                userAuthStateService.finishPermissionsUpdate(userId);
                userAuthStateService.markNotUpdating(userId);
            } catch (RuntimeException exception) {
                syncFailed = true;
                log.warn("角色权限已提交，用户权限同步失败，roleId={}，userId={}",
                        roleId, userId, exception);
            }
        }

        if (syncFailed) {
            throw new BusinessException(503, "角色权限已保存，部分用户权限同步未完成");
        }
    }
}
