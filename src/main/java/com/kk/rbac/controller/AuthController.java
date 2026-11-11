package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.dto.LoginRequest;
import com.kk.rbac.dto.LoginResponse;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.AuthService;
import com.kk.rbac.service.JwtService;
import com.kk.rbac.service.TokenRevocationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")//登录接口
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {

        SysUser user = authService.authenticate(
                loginRequest.getAccount(),
                loginRequest.getPassword()
        );
        String accessToken= jwtService.createToken(user.getId());

        LoginResponse response = new LoginResponse(
                user.getId(),
                user.getAccount(),
                user.getName(),
                accessToken
        );
        return ApiResponse.success(response);
    }
    @PostMapping("/logout")//退出登录接口
    public ApiResponse<Void> logout(@AuthenticationPrincipal LoginPrincipal principal) {
        try {
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
}
