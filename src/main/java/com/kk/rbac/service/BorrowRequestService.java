package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kk.rbac.common.constants.BorrowRequestConstants;
import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.BorrowRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BorrowRequestService {
    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    @Autowired
    private EquipmentService equipmentService;
    public Integer create(Integer deviceId, Integer borrowerId){
        if(deviceId==null||deviceId<=0)
            throw new BusinessException(400,"设备ID不合法");
        Equipment equipment=equipmentService.getEquipmentById(deviceId);
        if (!equipment.getStatus().equals(EquipmentConstants.AVAILABLE)) {
            throw new BusinessException(409, "设备当前不可申请");
        }
        BorrowRequest borrowRequest=new BorrowRequest();
        borrowRequest.setDeviceId(deviceId);
        borrowRequest.setBorrowerId(borrowerId);
        borrowRequest.setStatus(BorrowRequestConstants.PENDING);
        int result=borrowRequestMapper.insert(borrowRequest);
        if(result!=1){
            throw new BusinessException(500,"创建借用申请失败");
        }
        return borrowRequest.getId();
    }
    public List<BorrowRequest> listMine(Integer borrowerId){
        LambdaQueryWrapper<BorrowRequest> query=new LambdaQueryWrapper<>();
        query.eq(BorrowRequest::getBorrowerId,borrowerId);
        query.orderByDesc(BorrowRequest::getCreateTime);
        query.orderByDesc(BorrowRequest::getId);
        return borrowRequestMapper.selectList(query);
    }
}
