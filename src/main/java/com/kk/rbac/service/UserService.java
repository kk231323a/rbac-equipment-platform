package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.dto.PasswordChangeRequest;
import com.kk.rbac.dto.UserCreateRequest;
import com.kk.rbac.entity.SysRole;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.entity.SysUserRole;
import com.kk.rbac.mapper.AuthStateSyncTaskMapper;
import com.kk.rbac.mapper.SysRoleMapper;
import com.kk.rbac.mapper.SysUserMapper;
import com.kk.rbac.mapper.SysUserRoleMapper;
import com.kk.rbac.vo.RoleVO;
import com.kk.rbac.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@Service
public class UserService {
    @Autowired
    public SysUserMapper sysUserMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private UserAuthStateService userAuthStateService;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthStateSyncTaskMapper authStateSyncTaskMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Transactional
    @AuditOperation(
            actionCode = AuditConstants.USER_ENABLE,
            resourceType = AuditConstants.USER,
            resourceId = "#userId",
            operatorId = "#operatorId"
    )
    public void enableUser(Integer userId, Integer operatorId) {
        updateEnabled(userId, true, operatorId);
    }

    @Transactional
    @AuditOperation(
            actionCode = AuditConstants.USER_DISABLE,
            resourceType = AuditConstants.USER,
            resourceId = "#userId",
            operatorId = "#operatorId"
    )
    public void disableUser(Integer userId, Integer operatorId) {
        updateEnabled(userId, false, operatorId);
    }

    private void updateEnabled(Integer userId, boolean enable, Integer operatorId) {
        if (operatorId.equals(userId))
            throw new BusinessException(403, "不能修改自己的启用状态");

        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空且必须大于0");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userAuthStateService.markUpdating(user);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userAuthStateService.finishEnabledUpdate(userId, enable);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    userAuthStateService.markNotUpdating(userId);
                }
            }
        });

        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getId, userId).set(SysUser::getEnable, enable);
        int rows = sysUserMapper.update(update);
        if (rows != 1) throw new BusinessException(500, "更新用户状态失败");


    }

    public IPage<UserVO> listUsers(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum <= 0) {
            throw new BusinessException(400, "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(400, "每页数量必须在1到100之间");
        }
        Page<UserVO> page = new Page<>(pageNum, pageSize);

        page = sysUserMapper.selectAllUser(page);
        return page;
    }

    @AuditOperation(
            actionCode = AuditConstants.USER_ROLE_ASSIGN,
            resourceType = AuditConstants.USER,
            resourceId = "#userId",
            operatorId = "#operatorId",
            independent = true,
            details = "{'userId': #userId, 'roleId': #roleId}"
    )
    public void assignRole(Integer userId, Integer roleId, Integer operatorId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空且必须大于0");
        }
        if (roleId == null || roleId <= 0) {
            throw new BusinessException(400, "角色ID不能为空且必须大于0");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null)
            throw new BusinessException(404, "用户不存在");
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null)
            throw new BusinessException(404, "角色不存在");
        userAuthStateService.markUpdating(user);

        // 为用户分配角色
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);

        int rows;
        try {
            rows = sysUserRoleMapper.insert(userRole);
        } catch (DuplicateKeyException e) {
            userAuthStateService.markNotUpdating(userId);
            throw new BusinessException(409, "用户已拥有该角色");
        }

        if (rows != 1) {
            userAuthStateService.markNotUpdating(userId);
            throw new BusinessException(500, "角色分配失败");
        }
        userAuthStateService.finishPermissionsUpdate(userId);
        userAuthStateService.markNotUpdating(userId);
    }

    public List<RoleVO> listUserRoles(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空且必须大于0");
        }

        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return sysRoleMapper.selectRolesByUserId(userId);
    }

    @AuditOperation(
            actionCode = AuditConstants.USER_ROLE_REVOKE,
            resourceType = AuditConstants.USER,
            resourceId = "#userId",
            operatorId = "#operatorId",
            independent = true,
            details = "{'userId': #userId, 'roleId': #roleId}"
    )
    public void revokeRole(Integer userId, Integer roleId, Integer operatorId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空且必须大于0");
        }
        if (roleId == null || roleId <= 0) {
            throw new BusinessException(400, "角色ID不能为空且必须大于0");
        }
        if (userId.equals(operatorId)) {
            throw new BusinessException(403, "不能撤销自己的角色");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null)
            throw new BusinessException(404, "用户不存在");
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null)
            throw new BusinessException(404, "角色不存在");
        userAuthStateService.markUpdating(user);
        LambdaQueryWrapper<SysUserRole> condition = new LambdaQueryWrapper<>();
        condition.eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId);

        int rows = sysUserRoleMapper.delete(condition);
        if (rows != 1) {
            userAuthStateService.markNotUpdating(userId);
            throw new BusinessException(409, "用户未拥有该角色或已被撤销");
        }
        userAuthStateService.finishPermissionsUpdate(userId);
        userAuthStateService.markNotUpdating(userId);
    }

    @AuditOperation(actionCode = AuditConstants.USER_CREATE,
            resourceType = AuditConstants.USER,
            resourceId = "#result",
            independent = true
    )
    public Integer createUser(UserCreateRequest request) {
        String password = request.getPassword();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {//BCrypt 最多接受 72 个 UTF-8 字节
            throw new BusinessException(400, "密码过长");
        }

        SysUser user = new SysUser();
        user.setAccount(request.getAccount());
        user.setName(request.getName());
        user.setEnable(true);
        password = passwordEncoder.encode(password);
        user.setPasswordHash(password);
        int result;
        try {
            result = sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户已存在");
        }

        if (result != 1) {
            throw new BusinessException(500, "插入失败");
        }
        return user.getId();

    }

    public void changePassword(PasswordChangeRequest request) {
        String account = request.getAccount();
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getAccount, account);
        SysUser userAlive = sysUserMapper.selectOne(query);
        if (userAlive == null)
            throw new BusinessException(401, "账号或密码错误");
        String realPassword = userAlive.getPasswordHash();
        if (!passwordEncoder.matches(request.getOldPassword(), realPassword))
            throw new BusinessException(401, "账号或密码错误");
        //开始密码修改业务
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getAccount, account)
                .eq(SysUser::getPasswordHash, realPassword)
                .set(SysUser::getPasswordHash, passwordEncoder.encode(request.getNewPassword()));
        Integer userId = userAlive.getId();
//        修改前更改redis中状态
        long targetVersion = userAuthStateService.markUpdating(userAlive) + 1;

        transactionTemplate.executeWithoutResult(status -> {
            // 在这里注册事务回调，确认回滚后才解除 updating。

            if (sysUserMapper.update(null, update) != 1) {
                throw new BusinessException(500, "修改密码失败");
            }

            authStateSyncTaskMapper.save(userId, targetVersion);
        });

        userAuthStateService.incrJwtVersion(userId,targetVersion);//如果这段代码正常执行，则redis中的状态就能被正常修改，就不用入库，继续后面的删除库中存档
        //如果redis挂了，上面这段代码就会报错，下面这段删除就不会执行成功
        authStateSyncTaskMapper.delete(userId, targetVersion);

    }
}
