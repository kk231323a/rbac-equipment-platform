package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("equipment")
public class Equipment {
    @TableId(value = "id",type = IdType.AUTO)
   private Integer id;
   private String deviceNo;
   private String name;
   private String model;
   private String status;
   private LocalDateTime createTime;
   private LocalDateTime updateTime;
}
