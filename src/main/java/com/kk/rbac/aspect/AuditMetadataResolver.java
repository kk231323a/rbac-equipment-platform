package com.kk.rbac.aspect;

import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.entity.AuditLog;
import com.kk.rbac.service.AuditSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Component
public class AuditMetadataResolver {
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    public AuditLog resolve(ProceedingJoinPoint joinPoint, AuditOperation operation, Object result, boolean strict) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        var method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        var context = new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(), names);
        context.setVariable("result", result);
        AuditLog auditLog = new AuditLog();
        auditLog.setActionCode(operation.actionCode());
        auditLog.setResourceType(operation.resourceType());
        auditLog.setResourceId(positive(read(operation.resourceId(), context, Integer.class, strict)));
        if (operation.system()) {
            auditLog.setOperatorType(AuditConstants.OPERATOR_SYSTEM);
        } else {
            var principal = AuditSupport.principal();
            Integer operatorId = operation.operatorId().isBlank()
                    ? principal == null ? null : principal.userId()
                    : read(operation.operatorId(), context, Integer.class, strict);
            auditLog.setOperatorId(positive(operatorId));
            auditLog.setOperatorType(auditLog.getOperatorId() == null
                    ? AuditConstants.OPERATOR_ANONYMOUS : AuditConstants.OPERATOR_USER);
            auditLog.setOperatorAccount(AuditSupport.bounded(
                    read(operation.operatorAccount(), context, String.class, strict), 64));
        }
        if (strict && (auditLog.getResourceId() == null
                || !operation.system() && auditLog.getOperatorId() == null)) {
            throw new IllegalStateException("审计操作人或资源ID缺失：" + operation.actionCode());
        }
        AuditSupport.decorate(auditLog);
        return auditLog;
    }

    private Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private <T> T read(String expression, EvaluationContext context, Class<T> type, boolean strict) {
        if (expression.isBlank()) {
            return null;
        }
        try {
            return parser.parseExpression(expression).getValue(context, type);
        } catch (RuntimeException exception) {
            if (strict) {
                throw new IllegalStateException("审计表达式配置错误", exception);
            }
            return null;
        }
    }
}
