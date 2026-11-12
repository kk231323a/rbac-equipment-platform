package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.BorrowRequestMapper;
import com.kk.rbac.mapper.EquipmentMapper;
import com.kk.rbac.security.LoginPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
public class EquipmentService {
    @Autowired
    private EquipmentMapper equipmentMapper;

    public Equipment getEquipmentById(Integer id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException(404, EquipmentConstants.EQUIPMENT_NOT_EXIST);
        }
        return equipment;
    }

    public void reserve(Integer deviceId) {
        if (deviceId == null || deviceId <= 0) {
            throw new BusinessException(400, "设备ID不合法");
        }
        LambdaUpdateWrapper<Equipment> update = new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, deviceId)
                .eq(Equipment::getStatus, EquipmentConstants.AVAILABLE)
                .set(Equipment::getStatus, EquipmentConstants.RESERVED);
        int rows = equipmentMapper.update(null, update);//乐观锁预留
        if (rows != 1) {
            throw new BusinessException(409, "设备当前不可预留");
        }
    }

}
