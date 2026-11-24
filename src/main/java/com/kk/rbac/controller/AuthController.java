package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.dto.RefreshRequest;
import com.kk.rbac.dto.RefreshResponse;
import com.kk.rbac.security.UserAuthState;
import com.kk.rbac.service.*;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.dto.LoginRequest;
import com.kk.rbac.dto.LoginResponse;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.security.LoginPrincipal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private TokenRevocationService tokenRevocationService;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @PostMapping("/login")//登录接口
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {

        SysUser user = authService.authenticate(
                loginRequest.getAccount(),
                loginRequest.getPassword()
        );
        UserAuthState state = authService.prepareAuthState(user);
        String sid = UUID.randomUUID().toString();
        String accessToken = jwtService.createToken(user.getId(), state.tokenVersion(),sid);
        String refreshToken= refreshTokenService.createRefreshToken(sid, user.getId(), state.tokenVersion());

        LoginResponse response = new LoginResponse(
                user.getId(),
                user.getAccount(),
                user.getName(),
                accessToken,
                refreshToken
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions(Authentication authentication) {
        return ApiResponse.success(
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
    }

    @PostMapping("/logout")//退出登录接口
    @AuditOperation(actionCode = AuditConstants.LOGOUT, resourceType = AuditConstants.USER,
            resourceId = "#principal.userId()", operatorId = "#principal.userId()", independent = true)
    public ApiResponse<Void> logout(@AuthenticationPrincipal LoginPrincipal principal) {
        try {
            if (principal.sid() != null && !principal.sid().isBlank()) {
                refreshTokenService.revokeRefreshToken(principal.sid());
            }
            tokenRevocationService.revoke(
                    principal.jti(),
                    principal.expiresAt()
            );

        } catch (DataAccessException exception) {
            log.error("写入 Token 撤销记录失败", exception);
            throw new BusinessException(503, "退出失败，请稍后重试");
        }

        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(refreshTokenService.refresh(request.refreshToken()));
    }
}
