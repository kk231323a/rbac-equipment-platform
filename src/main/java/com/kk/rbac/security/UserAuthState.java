package com.kk.rbac.security;

import java.util.List;

public record UserAuthState(
        boolean enabled,
        Long tokenVersion,
        List<String> permissions,
        boolean updating
) {
}