package com.kk.rbac.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.BorrowRequestService;
import com.kk.rbac.service.EquipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@Slf4j
public class EquipmentController {
    @Autowired
    private EquipmentService equipmentService;
    @Autowired
    private BorrowRequestService borrowRequestService;

    @GetMapping("/{id}")
    public ApiResponse<Equipment> getEquipmentById(@PathVariable("id") Integer id) {
        log.info("查询设备信息，设备ID: {}", id);
        Equipment equipment = equipmentService.getEquipmentById(id);
        return ApiResponse.success(equipment);
    }


    @GetMapping("/borrowedList")
    public ApiResponse<List<BorrowRequest>> listMyBorrowRequests(@AuthenticationPrincipal LoginPrincipal loginPrincipal) {
        Integer borrowerId = loginPrincipal.userId();
        log.info("查询借用人ID: {}的借用申请", borrowerId);
        List<BorrowRequest> borrowRequests = borrowRequestService.listMine(borrowerId);
        return ApiResponse.success(borrowRequests);
    }
    @GetMapping("/available")
    public ApiResponse<IPage<Equipment>> listAvailable(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {

        return ApiResponse.success(
                equipmentService.listAvailable(pageNum, pageSize)
        );
    }


}
