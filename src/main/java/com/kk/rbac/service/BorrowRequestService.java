package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kk.rbac.common.constants.BorrowRequestConstants;
import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.BorrowRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BorrowRequestService {
    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    @Autowired
    private EquipmentService equipmentService;

    public Integer create(Integer deviceId, Integer borrowerId) {
        if (deviceId == null || deviceId <= 0)
            throw new BusinessException(400, "设备ID不合法");
        Equipment equipment = equipmentService.getEquipmentById(deviceId);
        if (!equipment.getStatus().equals(EquipmentConstants.AVAILABLE)) {
            throw new BusinessException(409, "设备当前不可申请");
        }
        BorrowRequest borrowRequest = new BorrowRequest();
        borrowRequest.setDeviceId(deviceId);
        borrowRequest.setBorrowerId(borrowerId);
        borrowRequest.setStatus(BorrowRequestConstants.PENDING);
        int result = borrowRequestMapper.insert(borrowRequest);
        if (result != 1) {
            throw new BusinessException(500, "创建借用申请失败");
        }
        return borrowRequest.getId();
    }

    public List<BorrowRequest> listMine(Integer borrowerId) {
        LambdaQueryWrapper<BorrowRequest> query = new LambdaQueryWrapper<>();
        query.eq(BorrowRequest::getBorrowerId, borrowerId);
        query.orderByDesc(BorrowRequest::getCreateTime);
        query.orderByDesc(BorrowRequest::getId);
        return borrowRequestMapper.selectList(query);
    }

    @Transactional
    public void approve(Integer requestId, Integer approverId, LocalDateTime dueTime) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(400, "申请ID不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        if (dueTime == null || !dueTime.isAfter(now)) {
            throw new BusinessException(400, "应还时间必须晚于当前时间");
        }
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null) {
            throw new BusinessException(404, BorrowRequestConstants.BORROW_REQUEST_NOT_EXIST);
        }
        if (!borrowRequest.getStatus().equals(BorrowRequestConstants.PENDING)) {
            throw new BusinessException(409, "借用申请已处理");
        }

        Integer deviceId = borrowRequest.getDeviceId();
        //先把设备状态改为预留，防止并发审批导致设备被重复借用
        equipmentService.reserve(deviceId);
        //把借用申请状态改为已批准
        LambdaUpdateWrapper<BorrowRequest> updateWrapper=new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.PENDING)
                .set(BorrowRequest::getStatus, BorrowRequestConstants.APPROVED)
                .set(BorrowRequest::getApproverId, approverId)
                .set(BorrowRequest::getDueTime, dueTime)
                .set(BorrowRequest::getApproveTime, LocalDateTime.now());
        //使用乐观锁更新借用申请状态，防止并发审批导致状态不一致
        int result = borrowRequestMapper.update(null,updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "借用申请已处理");
        }
    }
}
