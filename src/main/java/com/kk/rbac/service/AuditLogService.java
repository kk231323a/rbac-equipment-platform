package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.AuditLog;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.mapper.AuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    @Autowired
    private AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(Integer operatorId,
                              String actionCode,
                              String resourceType,
                              Integer resourceId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorId(operatorId);
        auditLog.setActionCode(actionCode);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setOperatorType(AuditConstants.OPERATOR_USER);
        auditLog.setResult(AuditConstants.SUCCESS);
        insert(auditLog);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(AuditLog auditLog) {
        insert(auditLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditLog auditLog) {
        insert(auditLog);
    }

    private void insert(AuditLog auditLog) {
        int rows = auditLogMapper.insert(auditLog);
        if (rows != 1) {
            throw new IllegalStateException("保存审计日志失败");
        }
    }

    public Page<AuditLog> logList(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum <= 0)
            throw new BusinessException(400, "请求参数非法");
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(400, "每页条数必须在1到100之间");
        }
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId);
        return auditLogMapper.selectPage(page, query);
    }
}
