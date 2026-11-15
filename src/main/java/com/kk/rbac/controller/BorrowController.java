package com.kk.rbac.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.BorrowRequestService;
import com.kk.rbac.service.EquipmentService;
import com.kk.rbac.vo.BorrowRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
    @PostMapping("/request/cancel")
    public ApiResponse<Void> cancelBorrowRequest(@RequestParam(value = "requestId", required = false) Integer requestId,
                                                 @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer borrowerId = loginPrincipal.userId();
        log.info("设备借用申请取消，申请ID: {}, 借用人ID: {}", requestId, borrowerId);
        borrowRequestService.cancel(requestId, borrowerId);
        return ApiResponse.success();
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
    @PostMapping("/reject")
    public ApiResponse<Void> rejectBorrowRequest(@RequestParam(value = "requestId", required = false) Integer requestId,
                                                 @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer approverId = loginPrincipal.userId();
        log.info("设备借用申请拒绝，申请ID: {}, 审批人ID: {}", requestId, approverId);
        borrowRequestService.reject(requestId, approverId);
        return ApiResponse.success();
    }
    @PostMapping("/receive")
    public ApiResponse<Void> receiveEquipment(@RequestParam(value = "requestId", required = false) Integer requestId,
                                          @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer receiverId = loginPrincipal.userId();
        log.info("设备借用申请确认领取，申请ID: {}, 收货人ID: {}", requestId, receiverId);
        if(!borrowRequestService.receive(requestId, receiverId)){
            throw new BusinessException(409,"已超过领取期限，申请已取消");
        }
        return ApiResponse.success();
    }
    @PostMapping("/return")
    public ApiResponse<Void> returnEquipment(@RequestParam(value = "requestId", required = false) Integer requestId,
                                             @AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer returnerId = loginPrincipal.userId();
        log.info("设备归还申请，申请ID: {}, 归还人ID: {}", requestId, returnerId);
        borrowRequestService.returnEquipment(requestId, returnerId);
        return ApiResponse.success();
    }
    @GetMapping("/pending")
    public ApiResponse<IPage<BorrowRequestVO>> listPending(
            @RequestParam(value = "pageNum", defaultValue = "1")
            Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20")
            Integer pageSize) {

        return ApiResponse.success(
                borrowRequestService.listPending(pageNum, pageSize)
        );
    }
    @GetMapping("/received")
    public ApiResponse<IPage<BorrowRequestVO>> listReceived(
            @RequestParam(value = "pageNum", defaultValue = "1")
            Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20")
            Integer pageSize) {

        return ApiResponse.success(
                borrowRequestService.listReceived(pageNum, pageSize)
        );
    }


}
