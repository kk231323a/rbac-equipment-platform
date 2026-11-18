package com.kk.rbac.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.UserService;
import com.kk.rbac.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {
    @Autowired
    public UserService userService;

    @PostMapping("/enable")
    public ApiResponse<Void> userEnable(Integer userId,@AuthenticationPrincipal LoginPrincipal principal) {
        Integer OperatorId = principal.userId();
        log.info("用户启用请求，用户ID: {},操作人ID:{}", userId, OperatorId);
        userService.enableUser(userId, OperatorId);
        return ApiResponse.success();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> userDisable(Integer userId, @AuthenticationPrincipal LoginPrincipal principal) {
        Integer OperatorId = principal.userId();
        log.info("用户禁用请求，用户ID: {},操作人ID:{}", userId, OperatorId);
        userService.disableUser(userId, OperatorId);
        return ApiResponse.success();
    }
    @GetMapping
    public ApiResponse<IPage<UserVO>> getAllUsers(@RequestParam(value = "pageNum",defaultValue = "1") Integer pageNum,
                                                  @RequestParam(value = "pageSize" ,defaultValue = "10") Integer pageSize) {
        log.info("获取所有用户请求，页码: {}, 每页大小: {}", pageNum, pageSize);
        IPage<UserVO> userPage = userService.listUsers(pageNum,pageSize);
        return ApiResponse.success(userPage);
    }
}
