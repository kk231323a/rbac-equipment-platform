package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.dto.LoginRequest;
import com.kk.rbac.dto.LoginResponse;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {

        SysUser user = authService.authenticate(
                loginRequest.getAccount(),
                loginRequest.getPassword()
        );

        LoginResponse response = new LoginResponse(
                user.getId(),
                user.getAccount(),
                user.getName()
        );

        return ApiResponse.success(response);
    }
}
