package com.kk.rbac.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Integer id;
    private String account;
    private String name;
    private Boolean enable;
    private LocalDateTime createTime;
}