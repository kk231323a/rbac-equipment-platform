package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.BorrowRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/borrow")
@Slf4j
public class BorrowController {
    @Autowired
    private BorrowRequestService borrowRequestService;

    @PostMapping("/request")
    public ApiResponse<Integer> borrowEquipment(@RequestParam(value = "deviceId", required = false) Integer deviceId,
                                                @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer borrowerId = loginPrincipal.userId();
        log.info("设备借用申请，设备ID: {}, 借用人ID: {}", deviceId, borrowerId);
        Integer requestId = borrowRequestService.create(deviceId, borrowerId);
        return ApiResponse.success(requestId);
    }

    @PostMapping("/approve")
    public ApiResponse<Void> approveBorrowRequest(@RequestParam(value = "requestId", required = false) Integer requestId,
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueTime,
                                                  @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer approverId = loginPrincipal.userId();
        log.info("设备借用申请审批，申请ID: {}, 审批人ID: {}, 应还时间:{}", requestId, approverId, dueTime);
        borrowRequestService.approve(requestId, approverId, dueTime);
        return ApiResponse.success();
    }
}
