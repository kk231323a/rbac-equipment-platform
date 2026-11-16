package com.kk.rbac.service;

import com.kk.rbac.entity.AuditLog;
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
                              Integer resourceId){
        AuditLog auditLog=new AuditLog();
        auditLog.setOperatorId(operatorId);
        auditLog.setActionCode(actionCode);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setResult("success");
        int rows = auditLogMapper.insert(auditLog);
        if (rows != 1) {
            throw new IllegalStateException("保存审计日志失败");
        }
    }
}
