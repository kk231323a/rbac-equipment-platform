package com.kk.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeRequest {
    @NotBlank(message = "账号不能为空")
    @Pattern(
            regexp = "^[A-Za-z0-9_]{3,50}$",
            message = "账号应为3到50位字母、数字或下划线"
    )
    String account;

    @NotBlank(message = "旧密码不能为空")
    @Size(max = 72, message = "密码长度应为0到72个字符")
    String oldPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度应为6到72个字符")
    @Pattern(regexp = "\\p{ASCII}+", message = "密码仅支持单字节 ASCII 字符")
    String newPassword;
}
