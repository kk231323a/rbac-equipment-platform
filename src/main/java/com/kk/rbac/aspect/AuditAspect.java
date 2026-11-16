package com.kk.rbac.aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.service.AuditLogService;
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

    @Around("@annotation(auditOperation)")
    public Object record(ProceedingJoinPoint joinPoint,
                         AuditOperation auditOperation) throws Throwable {

        Object result = joinPoint.proceed();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(
                        null,
                        signature.getMethod(),
                        joinPoint.getArgs(),
                        new DefaultParameterNameDiscoverer()
                );

        SpelExpressionParser parser = new SpelExpressionParser();

        Integer operatorId = parser.parseExpression(auditOperation.operatorId())
                .getValue(context, Integer.class);

        Integer resourceId = parser.parseExpression(auditOperation.resourceId())
                .getValue(context, Integer.class);
        auditLogService.recordSuccess(
                operatorId,
                auditOperation.actionCode(),
                auditOperation.resourceType(),
                resourceId
        );

        return result;
    }
}