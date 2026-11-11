package com.kk.rbac.security;

import java.time.Instant;

public record LoginPrincipal(
        Integer userId,
        String jti,
        Instant expiresAt
) {
}
