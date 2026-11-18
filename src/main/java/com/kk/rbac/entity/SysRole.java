package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String roleCode;
    private String roleName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
