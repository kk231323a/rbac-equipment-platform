package com.kk.rbac.dto;

public record RefreshResponse(String accessToken, String refreshToken) {
}