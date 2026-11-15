package com.kk.rbac.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.dto.EquipmentCreateRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.security.LoginPrincipal;
import com.kk.rbac.service.BorrowRequestService;
import com.kk.rbac.service.EquipmentService;
import jakarta.validation.Valid;
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


    @GetMapping("/available")
    public ApiResponse<IPage<Equipment>> listAvailable(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {

        return ApiResponse.success(
                equipmentService.listAvailable(pageNum, pageSize)
        );
    }

    @PostMapping("addNew")
    public ApiResponse<Void> addNewEquipment(@RequestBody @Valid EquipmentCreateRequest equipmentCreateRequest) {
        log.info("添加新设备: {}", equipmentCreateRequest);
        equipmentService.addNewEquipment(equipmentCreateRequest);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<IPage<Equipment>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "status", required = false) String status) {

        return ApiResponse.success(equipmentService.listEquipment(pageNum, pageSize, status));
    }


}
