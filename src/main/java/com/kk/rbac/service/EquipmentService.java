package com.kk.rbac.service;

import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.EquipmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EquipmentService {
    @Autowired
    private EquipmentMapper equipmentMapper;

    public Equipment getEquipmentById(Integer id) {
        Equipment equipment=equipmentMapper.selectById(id);
        if (equipment==null){
            throw new BusinessException(404, EquipmentConstants.EQUIPMENT_NOT_EXIST);
        }
        return equipment;
    }
}
