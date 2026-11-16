package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.AuditConstants;
import com.kk.rbac.common.constants.BorrowRequestConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.mapper.BorrowRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class BorrowTimeoutService {
    @Autowired
    private BorrowRequestMapper borrowRequestMapper;
    @Autowired
    private EquipmentService equipmentService;

    // 独立 Bean 保证定时任务和领取时的取消都经过 Spring 代理。
    @Transactional
    @AuditOperation(actionCode = AuditConstants.BORROW_EXPIRE, resourceType = AuditConstants.BORROW_REQUEST,
            resourceId = "#requestId", system = true)
    public boolean cancelExpired(Integer requestId) {
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null
                || !BorrowRequestConstants.APPROVED.equals(borrowRequest.getStatus())
                || borrowRequest.getApproveTime() == null) {
            return false;
        }
        LocalDateTime deadline = borrowRequest.getApproveTime().toLocalDate().plusDays(2).atTime(12, 0);
        if (LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isBefore(deadline)) {
            return false;
        }
        equipmentService.releaseReservation(borrowRequest.getDeviceId());
        LambdaUpdateWrapper<BorrowRequest> update = new LambdaUpdateWrapper<>();
        update.eq(BorrowRequest::getId, requestId)
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.APPROVED)
                .eq(BorrowRequest::getApproveTime, borrowRequest.getApproveTime())
                .set(BorrowRequest::getStatus, BorrowRequestConstants.CANCEL);
        if (borrowRequestMapper.update(null, update) != 1) {
            throw new BusinessException(409, "申请状态已变化，超时取消失败");
        }
        return true;
    }
}
