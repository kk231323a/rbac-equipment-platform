package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer roleId;
}
