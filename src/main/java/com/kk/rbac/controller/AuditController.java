package com.kk.rbac.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.entity.AuditLog;
import com.kk.rbac.service.AuditLogService;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Slf4j
public class AuditController {

    @Autowired
    public AuditLogService auditLogService;

    @GetMapping("/logs")
    public ApiResponse<IPage<AuditLog>> logList(@RequestParam(value = "pageNum",defaultValue = "1") Integer pageNum,
                                                @RequestParam(value = "pageSize",defaultValue = "10") Integer pageSize) {
        Page<AuditLog> page = auditLogService.logList(pageNum, pageSize);
        return ApiResponse.success(page);
    }
}
