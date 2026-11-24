package com.kk.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "刷新凭证不能为空") String refreshToken
) {
}