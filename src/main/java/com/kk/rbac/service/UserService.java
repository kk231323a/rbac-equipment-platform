package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.mapper.SysUserMapper;
import com.kk.rbac.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
public class UserService {
    @Autowired
    public SysUserMapper sysUserMapper;

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
    private void updateEnabled(Integer userId, boolean enable, Integer operatorId){
        if (operatorId.equals(userId))
            throw new BusinessException(403, "不能修改自己的启用状态");

        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空且必须大于0");
        }

        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getId, userId).set(SysUser::getEnable, enable);
        int rows = sysUserMapper.update(update);
        if (rows != 1) {
            throw new BusinessException(404,"用户不存在");
        }
    }

    public IPage<UserVO> listUsers(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum <= 0) {
            throw new BusinessException(400, "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0||pageSize>100) {
            throw new BusinessException(400, "每页数量必须在1到100之间");
        }
        Page<UserVO> page=new Page<>(pageNum,pageSize);

        page=sysUserMapper.selectAllUser(page);
        return page;
    }
}
