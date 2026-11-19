package com.kk.rbac.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.AuditLog;
import com.kk.rbac.security.LoginPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class AuditSupport {
    public static final String LOGIN_ACCOUNT_ATTRIBUTE = AuditSupport.class.getName() + ".account";
    private static final String HANDLED_ATTRIBUTE = AuditSupport.class.getName() + ".handled";

    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private ObjectMapper objectMapper;

    public static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    public static LoginPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof LoginPrincipal principal
                ? principal : null;
    }

    public static String bounded(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.codePoints().filter(code -> !Character.isISOControl(code)).limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public static int errorCode(Throwable exception) {
        return exception instanceof BusinessException business ? business.getCode() : 500;
    }

    public static String errorMessage(Throwable exception) {
        // JDBC、JSON 等异常消息可能包含 SQL 参数或请求内容，只保留受控业务提示。
        return exception instanceof BusinessException business
                ? bounded(business.getMessage(), 255) : "系统异常";
    }

    public static void decorate(AuditLog auditLog) {
        Route route = route(currentRequest());
        if (route != null) {
            auditLog.setRequestPath(route.path());
        }
    }

    public void independent(AuditLog auditLog) {
        try {
            auditLogService.recordIndependent(auditLog);
        } catch (RuntimeException exception) {
            unavailable(auditLog.getActionCode(), exception);
        }
    }

    public void unavailable(String action, RuntimeException exception) {
        // 独立日志失败不能覆盖原业务异常，也不能把已完成的 Redis 退出改报失败。
        log.error("独立审计保存失败，动作：{}，异常类型：{}", action, exception.getClass().getSimpleName());
    }

    public void failureAfterTransaction(AuditLog auditLog) {
        markHandled(currentRequest(), auditLog.getActionCode());
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    independent(auditLog);
                }
            });
        } else {
            independent(auditLog);
        }
    }

    public void httpFailure(HttpServletRequest request, int code, String message, String account) {
        Route route = route(request);
        if (route == null || handled(request, route.action())) {
            return;
        }
        AuditLog auditLog = new AuditLog();
        LoginPrincipal principal = principal();
        // 登录失败时账号只代表尝试登录的账号，不能据此认定操作人身份。
        boolean login = AuditConstants.LOGIN.equals(route.action());
        auditLog.setOperatorId(!login && principal != null ? principal.userId() : null);
        auditLog.setOperatorType(auditLog.getOperatorId() == null
                ? AuditConstants.OPERATOR_ANONYMOUS : AuditConstants.OPERATOR_USER);
        Object attemptedAccount = request.getAttribute(LOGIN_ACCOUNT_ATTRIBUTE);
        auditLog.setOperatorAccount(login ? bounded(account != null ? account
                : attemptedAccount instanceof String value ? value : null, 64) : null);
        auditLog.setActionCode(route.action());
        auditLog.setResourceType(route.resourceType());
        auditLog.setResourceId(route.resourceId());
        auditLog.setResult(AuditConstants.FAILURE);
        auditLog.setErrorCode(code);
        auditLog.setErrorMessage(bounded(message, 255));
        auditLog.setRequestPath(route.path());
        if (AuditConstants.USER_ROLE_ASSIGN.equals(route.action())
                || AuditConstants.USER_ROLE_REVOKE.equals(route.action())) {
            try {
                var details = new LinkedHashMap<String, Integer>();
                details.put("userId", route.resourceId());
                details.put("roleId", positiveId(request.getParameter("roleId")));
                auditLog.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException | RuntimeException e) {
                log.error("角色变更审计明细生成失败，异常类型：{}",
                        e.getClass().getSimpleName());
            }
        }else if (AuditConstants.USER_PERMISSION_ASSIGN.equals(route.action())
                || AuditConstants.USER_PERMISSION_REVOKE.equals(route.action())) {
            try {
                var details = new LinkedHashMap<String, Integer>();
                details.put("roleId", route.resourceId());
                details.put(
                        "permissionId",
                        positiveId(request.getParameter("permissionId"))
                );
                auditLog.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException | RuntimeException exception) {
                log.error("角色权限审计明细生成失败，异常类型：{}",
                        exception.getClass().getSimpleName());
            }
        }
        markHandled(request, route.action());
        independent(auditLog);
    }

    @SuppressWarnings("unchecked")
    private static boolean handled(HttpServletRequest request, String action) {
        Object value = request.getAttribute(HANDLED_ATTRIBUTE);
        return value instanceof Set<?> set && set.contains(action);
    }

    @SuppressWarnings("unchecked")
    private static void markHandled(HttpServletRequest request, String action) {
        if (request == null) {
            return;
        }
        Object existing = request.getAttribute(HANDLED_ATTRIBUTE);
        Set<String> actions;
        if (existing instanceof Set<?>) {
            actions = (Set<String>) existing;
        } else {
            actions = new HashSet<>();
            request.setAttribute(HANDLED_ATTRIBUTE, actions);
        }
        actions.add(action);
    }

    private record Route(String action, String resourceType, Integer resourceId, String path) {
    }

    private static Integer positiveId(String value) {
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Route route(HttpServletRequest request) {//从 HTTP 请求中识别“这次操作该怎么记审计日志”
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("PUT".equals(request.getMethod()) && path.matches("/api/equipment/update/[^/]+")) {
            return new Route(AuditConstants.EQUIPMENT_UPDATE, AuditConstants.EQUIPMENT,
                    positiveId(path.substring(path.lastIndexOf('/') + 1)), "/api/equipment/update/{id}");
        }
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String action = switch (path) {
            case "/api/auth/login" -> AuditConstants.LOGIN;
            case "/api/auth/logout" -> AuditConstants.LOGOUT;
            case "/api/equipment/addNew" -> AuditConstants.EQUIPMENT_CREATE;
            case "/api/borrow/request" -> AuditConstants.BORROW_CREATE;
            case "/api/borrow/approve" -> AuditConstants.BORROW_APPROVE;
            case "/api/borrow/reject" -> AuditConstants.BORROW_REJECT;
            case "/api/borrow/receive" -> AuditConstants.BORROW_RECEIVE;
            case "/api/borrow/return" -> AuditConstants.BORROW_RETURN;
            case "/api/borrow/request/cancel" -> AuditConstants.BORROW_CANCEL;
            case "/api/user/enable" -> AuditConstants.USER_ENABLE;
            case "/api/user/disable" -> AuditConstants.USER_DISABLE;
            case "/api/user/role/assign" -> AuditConstants.USER_ROLE_ASSIGN;
            case "/api/user/role/revoke" -> AuditConstants.USER_ROLE_REVOKE;
            case "/api/permissions/assign" -> AuditConstants.USER_PERMISSION_ASSIGN;
            case "/api/permissions/revoke" -> AuditConstants.USER_PERMISSION_REVOKE;
            default -> null;
        };
        if (action == null) {
            return null;
        }
        if (path.startsWith("/api/auth/")) {
            LoginPrincipal principal = principal();
            return new Route(action, AuditConstants.USER,
                    AuditConstants.LOGOUT.equals(action) && principal != null ? principal.userId() : null, path);
        }
        if (AuditConstants.USER_ENABLE.equals(action)
                || AuditConstants.USER_DISABLE.equals(action)
                || AuditConstants.USER_ROLE_ASSIGN.equals(action)
                || AuditConstants.USER_ROLE_REVOKE.equals(action)) {
            return new Route(
                    action,
                    AuditConstants.USER,
                    positiveId(request.getParameter("userId")),
                    path
            );
        }
        if (AuditConstants.EQUIPMENT_CREATE.equals(action)) {
            return new Route(action, AuditConstants.EQUIPMENT, null, path);
        }
        if (AuditConstants.USER_PERMISSION_ASSIGN.equals(action)||AuditConstants.USER_PERMISSION_REVOKE.equals(action)) {
            return new Route(
                    action,
                    AuditConstants.ROLE,
                    positiveId(request.getParameter("roleId")),
                    path
            );
        }
        return new Route(action, AuditConstants.BORROW_REQUEST,
                AuditConstants.BORROW_CREATE.equals(action) ? null : positiveId(request.getParameter("requestId")), path);
    }
}
