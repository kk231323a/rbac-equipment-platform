package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.service.RoleService;
import com.kk.rbac.vo.PermissionVO;
import com.kk.rbac.vo.RoleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    @Autowired
    private RoleService roleService;
    @GetMapping
    public ApiResponse<List<RoleVO>> getAllRoles() {
        List<RoleVO> roles = roleService.getAllRoles();
        return ApiResponse.success(roles);
    }
    @GetMapping("/{roleId}/permissions")
    public ApiResponse<List<PermissionVO>> listRolePermissions(
            @PathVariable("roleId") Integer roleId) {

        return ApiResponse.success(
                roleService.listRolePermissions(roleId)
        );
    }

}
