package com.kk.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateRequest {

    @NotBlank(message = "账号不能为空")
    @Pattern(
            regexp = "^[A-Za-z0-9_]{3,50}$",
            message = "账号应为3到50位字母、数字或下划线"
    )
    private String account;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字符")
    private String name;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度应为8到72个字符")
    private String password;
}