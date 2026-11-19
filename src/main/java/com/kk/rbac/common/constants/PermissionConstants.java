package com.kk.rbac.common.constants;

public final class PermissionConstants {

    public static final String EQUIPMENT_READ = "equipment:read";
    public static final String EQUIPMENT_CREATE = "equipment:create";
    public static final String BORROW_CREATE = "borrow:create";
    public static final String BORROW_APPROVED = "borrow:approved";
    public static final String BORROW_RETURN = "borrow:return";
    public static final String EQUIPMENT_UPDATE = "equipment:update";

    public static final String AUDIT_READ = "audit:read";
    public static final String USER_ENABLE = "user:enable";
    public static final String USER_DISABLE = "user:disable";
    public static final String USER_READ = "user:read";
    public static final String USER_ROLE_ASSIGN = "user:role:assign";
    public static final String USER_ROLE_REVOKE = "user:role:revoke";
    public static final String USER_PERMISSION_ASSIGN = "role:permission:assign";
    public static final String USER_PERMISSION_REVOKE = "role:permission:revoke";


    private PermissionConstants() {
    }
}