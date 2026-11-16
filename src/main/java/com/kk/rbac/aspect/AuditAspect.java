package com.kk.rbac.aspect;
import org.springframework.core.annotation.Order;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.service.AuditLogService;
import com.kk.rbac.service.AuditSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class AuditAspect {
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private AuditMetadataResolver resolver;
    @Autowired
    private AuditSupport auditSupport;

    @Around("@annotation(auditOperation)")
    public Object record(ProceedingJoinPoint joinPoint,
                         AuditOperation auditOperation) throws Throwable {

        Object result = joinPoint.proceed();
        if (Boolean.FALSE.equals(result)) {
            return result;
        }
        if (auditOperation.independent()) {
            try {
                var auditLog = resolver.resolve(joinPoint, auditOperation, result, true);
                auditLog.setResult(AuditConstants.SUCCESS);
                auditSupport.independent(auditLog);
            } catch (RuntimeException exception) {
                auditSupport.unavailable(auditOperation.actionCode(), exception);
            }
        } else {
            var auditLog = resolver.resolve(joinPoint, auditOperation, result, true);
            auditLog.setResult(AuditConstants.SUCCESS);
            auditLogService.recordSuccess(auditLog);
        }

        return result;
    }
}
