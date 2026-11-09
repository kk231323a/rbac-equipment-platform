package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_user")
public class SysUser {
    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;
    private String account;
    private String name;
    private String passwordHash;
    private Boolean enable;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
