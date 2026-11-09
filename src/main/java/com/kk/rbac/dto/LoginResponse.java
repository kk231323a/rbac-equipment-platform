package com.kk.rbac.dto;

public record LoginResponse(
        Integer id,
        String account,
        String name
) {
}