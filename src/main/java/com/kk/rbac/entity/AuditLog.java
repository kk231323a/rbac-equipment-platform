package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Integer operatorId;
    private String operatorType;
    private String operatorAccount;
    private String actionCode;
    private String resourceType;
    private Integer resourceId;
    private String result;
    private Integer errorCode;
    private String errorMessage;
    private String requestPath;
    private LocalDateTime createTime;
}
