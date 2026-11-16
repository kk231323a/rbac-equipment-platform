package com.kk.rbac.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    String actionCode();

    String resourceType();

    String resourceId() default "";

    String operatorId() default "";

    String operatorAccount() default "";

    boolean system() default false;

    // 登录审计不阻断认证；Redis 退出无法与 MySQL 共享本地事务，独立审计保留原操作结果。
    boolean independent() default false;

    // 领取超时返回 false 表示未领取；超时扫描返回 false 则只是未处理。
    boolean falseIsFailure() default false;
}
