package com.kk.rbac.aspect;

import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.service.AuditSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(-1)
public class AuditFailureAspect {
    @Autowired
    private AuditMetadataResolver resolver;
    @Autowired
    private AuditSupport auditSupport;

    @Around("@annotation(operation)")
    public Object recordFailure(ProceedingJoinPoint joinPoint, AuditOperation operation) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable exception) {
            var auditLog = resolver.resolve(joinPoint, operation, null, false);//解析器组装日志
            auditLog.setResult(AuditConstants.FAILURE);
            auditLog.setErrorCode(AuditSupport.errorCode(exception));
            auditLog.setErrorMessage(AuditSupport.errorMessage(exception));
            auditSupport.failureAfterTransaction(auditLog);
            throw exception;
        }
        if (operation.falseIsFailure() && Boolean.FALSE.equals(result)) {
            var auditLog = resolver.resolve(joinPoint, operation, result, false);
            auditLog.setResult(AuditConstants.FAILURE);
            auditLog.setErrorCode(409);
            auditLog.setErrorMessage("已超过领取期限，申请已取消");
            auditSupport.failureAfterTransaction(auditLog);
        }
        return result;
    }
}
