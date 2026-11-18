package com.kk.rbac.common.constants;

public final class AuditConstants {
    public static final String LOGIN = "auth:login";
    public static final String LOGOUT = "auth:logout";
    public static final String EQUIPMENT_CREATE = "equipment:create";
    public static final String EQUIPMENT_UPDATE = "equipment:update";
    public static final String BORROW_CREATE = "borrow:create";
    public static final String BORROW_APPROVE = "borrow:approved";
    public static final String BORROW_REJECT = "borrow:reject";
    public static final String BORROW_RECEIVE = "borrow:receive";
    public static final String BORROW_RETURN = "borrow:return";
    public static final String BORROW_CANCEL = "borrow:cancel";
    public static final String BORROW_EXPIRE = "borrow:cancel-expired";
    public static final String BORROW_REQUEST = "borrow_request";
    public static final String EQUIPMENT = "equipment";
    public static final String USER = "sys_user";
    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";
    public static final String OPERATOR_USER = "user";
    public static final String OPERATOR_SYSTEM = "system";
    public static final String OPERATOR_ANONYMOUS = "anonymous";
    public static final String USER_ENABLE = "user:enable";
    public static final String USER_DISABLE = "user:disable";
    public static final String USER_ROLE_ASSIGN = "user:role:assign";

    private AuditConstants() {
    }
}
