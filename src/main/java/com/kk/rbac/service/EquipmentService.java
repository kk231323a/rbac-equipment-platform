package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.dto.EquipmentCreateRequest;
import com.kk.rbac.dto.EquipmentUpdateRequest;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.BorrowRequestMapper;
import com.kk.rbac.mapper.EquipmentMapper;
import com.kk.rbac.security.LoginPrincipal;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;

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

    public void reserve(Integer deviceId) {//可用状态转为预留
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

    public void borrow(Integer deviceId) {//预留状态转为借用
        if (deviceId == null || deviceId <= 0) {
            throw new BusinessException(400, "设备ID不合法");
        }
        LambdaUpdateWrapper<Equipment> update = new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, deviceId)
                .eq(Equipment::getStatus, EquipmentConstants.RESERVED)
                .set(Equipment::getStatus, EquipmentConstants.BORROWED);
        int rows = equipmentMapper.update(null, update);//乐观锁借用
        if (rows != 1) {
            throw new BusinessException(409, "设备当前不可借用");
        }
    }

    public void returnEquipment(Integer deviceId) {//借用状态转为可用//归还设备
        if (deviceId == null || deviceId <= 0) {
            throw new BusinessException(400, "设备ID不合法");
        }
        LambdaUpdateWrapper<Equipment> update = new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, deviceId)
                .eq(Equipment::getStatus, EquipmentConstants.BORROWED)
                .set(Equipment::getStatus, EquipmentConstants.AVAILABLE);
        int rows = equipmentMapper.update(null, update);//乐观锁归还
        if (rows != 1) {
            throw new BusinessException(409, "设备当前不可归还");
        }
    }

    public void releaseReservation(Integer deviceId) {
        if (deviceId == null || deviceId <= 0) {
            throw new BusinessException(400, "设备ID不合法");
        }
        LambdaUpdateWrapper<Equipment> update = new LambdaUpdateWrapper<Equipment>()
                .eq(Equipment::getId, deviceId)
                .eq(Equipment::getStatus, EquipmentConstants.RESERVED)
                .set(Equipment::getStatus, EquipmentConstants.AVAILABLE);
        int rows = equipmentMapper.update(null, update);//乐观锁释放预留
        if (rows != 1) {
            throw new BusinessException(409, "设备当前不可释放预留");
        }
    }

    public IPage<Equipment> listAvailable(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum <= 0) {
            throw new BusinessException(400, "页码不合法");
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(400, "页大小不合法");
        }
        Page<Equipment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Equipment> query =
                new LambdaQueryWrapper<Equipment>()
                        .eq(Equipment::getStatus, EquipmentConstants.AVAILABLE)
                        .orderByAsc(Equipment::getId);
        page = equipmentMapper.selectPage(page, query);

        return page;
    }

    public void addNewEquipment(EquipmentCreateRequest equipmentCreateRequest) {
        if (equipmentCreateRequest == null) {
            throw new BusinessException(400, "设备信息不能为空");
        }
        Equipment equipment = new Equipment();
        BeanUtils.copyProperties(equipmentCreateRequest, equipment);
        int result;
        try {
            result = equipmentMapper.insert(equipment);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "设备编号已存在");
        }

        if (result != 1) {
            throw new BusinessException(500, "添加设备失败");
        }
    }

    public IPage<Equipment> listEquipment(Integer pageNum, Integer pageSize, String status) {
        if (pageNum == null || pageNum <= 0) {
            throw new BusinessException(400, "页码不合法");
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(400, "页大小不合法");
        }
        if (status != null) {
            status = status.trim();
            if (status.isBlank()) {
                status = null;
            } else if (!EquipmentConstants.AVAILABLE.equals(status)
                    && !EquipmentConstants.RESERVED.equals(status)
                    && !EquipmentConstants.BORROWED.equals(status)) {
                throw new BusinessException(400, "设备状态不合法");
            }
        }
        Page<Equipment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        query.eq(status != null, Equipment::getStatus, status);
        query.orderByAsc(Equipment::getId);
        page = equipmentMapper.selectPage(page, query);
        return page;
    }
    public void updateEquipment(EquipmentUpdateRequest equipmentUpdateRequest,Integer deviceId) {
        if (equipmentUpdateRequest == null) {
            throw new BusinessException(400, "设备信息不能为空");
        }
        if (deviceId == null || deviceId <= 0) {
            throw new BusinessException(400, "设备ID不合法");
        }
        Equipment equipment = new Equipment();
        BeanUtils.copyProperties(equipmentUpdateRequest, equipment);
        equipment.setId(deviceId);
        int rows;

        rows = equipmentMapper.updateById(equipment);

        if (rows == 0) {
            throw new BusinessException(404, EquipmentConstants.EQUIPMENT_NOT_EXIST);
        }
    }
}
