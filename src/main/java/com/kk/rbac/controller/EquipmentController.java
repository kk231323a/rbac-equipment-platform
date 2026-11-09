package com.kk.rbac.controller;

import com.kk.rbac.common.ApiResponse;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.service.EquipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
    @Autowired
    private EquipmentService equipmentService;
    @GetMapping("/{id}")
    public ApiResponse<Equipment> getEquipmentById(@PathVariable("id") Integer id){
        Equipment equipment=equipmentService.getEquipmentById(id);
        return ApiResponse.success(equipment);
    }
}
