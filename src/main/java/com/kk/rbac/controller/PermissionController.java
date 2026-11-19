package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.PermissionService;
import com.kk.rbac.service.RoleService;
import com.kk.rbac.vo.PermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private RoleService roleService;

    @GetMapping
    public ApiResponse<List<PermissionVO>> getAllPermission(){

        return ApiResponse.success(permissionService.listAllPermissions());
    }
    @PostMapping("/assign")
    public ApiResponse<Void> assignPermissionForRole(Integer permissionId,Integer roleId){
        LoginPrincipal principal =(LoginPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        roleService.assignPermission(roleId,permissionId,principal.userId());
        return ApiResponse.success();
    }
    @PostMapping("/revoke")
    public ApiResponse<Void> revokePermissionForRole(Integer permissionId,Integer roleId){
        LoginPrincipal principal =(LoginPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        roleService.revokePermission(roleId,permissionId,principal.userId());
        return ApiResponse.success();
    }
}
