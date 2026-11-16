package com.kk.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.common.annotation.AuditOperation;
import com.kk.rbac.common.constants.BorrowRequestConstants;
import com.kk.rbac.common.constants.EquipmentConstants;
import com.kk.rbac.common.constants.PermissionConstants;
import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.entity.Equipment;
import com.kk.rbac.mapper.BorrowRequestMapper;
import com.kk.rbac.vo.BorrowRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
    @AuditOperation(
            actionCode = PermissionConstants.BORROW_APPROVED,
            resourceType = "borrow_request",
            resourceId = "#requestId",
            operatorId = "#approverId")
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
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.PENDING)
                .set(BorrowRequest::getStatus, BorrowRequestConstants.APPROVED)
                .set(BorrowRequest::getApproverId, approverId)
                .set(BorrowRequest::getDueTime, dueTime)
                .set(BorrowRequest::getApproveTime, LocalDateTime.now());
        //使用乐观锁更新借用申请状态，防止并发审批导致状态不一致
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "借用申请已处理");
        }
    }
    @Transactional
    @AuditOperation(
            actionCode = "borrow:reject",
            resourceType = "borrow_request",
            resourceId = "#requestId",
            operatorId = "#approverId"
    )
    public void reject(Integer requestId, Integer approverId) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(400, "申请ID不合法");
        }
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null) {
            throw new BusinessException(404, BorrowRequestConstants.BORROW_REQUEST_NOT_EXIST);
        }
        if (!borrowRequest.getStatus().equals(BorrowRequestConstants.PENDING)) {
            throw new BusinessException(409, "借用申请已处理");
        }
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.PENDING)
                .set(BorrowRequest::getStatus, BorrowRequestConstants.REJECTED)
                .set(BorrowRequest::getApproverId, approverId)
                .set(BorrowRequest::getApproveTime, LocalDateTime.now());
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "借用申请已处理");
        }
    }

    @Transactional
    @AuditOperation(
            actionCode = "borrow:receive",
            resourceType = "borrow_request",
            resourceId = "#requestId",
            operatorId = "#receiverId")
    public boolean receive(Integer requestId, Integer receiverId) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(400, "申请ID不合法");
        }
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null) {
            throw new BusinessException(404, BorrowRequestConstants.BORROW_REQUEST_NOT_EXIST);
        }
        if (!borrowRequest.getStatus().equals(BorrowRequestConstants.APPROVED)) {
            throw new BusinessException(409, "借用申请未批准");
        }
        if (!borrowRequest.getBorrowerId().equals(receiverId)) {
            throw new BusinessException(403, "不能领取他人申请的设备");
        }
        if (borrowRequest.getApproveTime() == null) {
            throw new BusinessException(409, "申请缺少审批时间，无法领用");
        }

        LocalDateTime deadline = borrowRequest.getApproveTime()
                .toLocalDate()
                .plusDays(2)
                .atTime(12, 0);

        LocalDateTime now = LocalDateTime.now(
                ZoneId.of("Asia/Shanghai")
        );

        if (!now.isBefore(deadline)) {
            if (!cancelExpired(requestId)) {
                throw new BusinessException(409, "申请状态已变化，请刷新后重试");
            }
            return false;
        }
        equipmentService.borrow(borrowRequest.getDeviceId());
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.APPROVED)
                .set(BorrowRequest::getStatus, BorrowRequestConstants.RECEIVED)
                .set(BorrowRequest::getBorrowTime, LocalDateTime.now());
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "借用申请已处理");
        }
        return true;
    }

    @Transactional
    @AuditOperation(actionCode = "borrow:return", resourceType = "borrow_request",
            operatorId = "#returnerId", resourceId = "#requestId")
    public void returnEquipment(Integer requestId, Integer returnerId) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(400, "申请ID不合法");
        }
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null) {
            throw new BusinessException(404, BorrowRequestConstants.BORROW_REQUEST_NOT_EXIST);
        }
        if (!borrowRequest.getStatus().equals(BorrowRequestConstants.RECEIVED)) {
            throw new BusinessException(409, "借用申请未领取");
        }
        equipmentService.returnEquipment(borrowRequest.getDeviceId());
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.RECEIVED)
                .set(BorrowRequest::getReturnTime, LocalDateTime.now())
                .set(BorrowRequest::getStatus, BorrowRequestConstants.RETURNED);
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "借用申请已处理");
        }
    }

    @Transactional
    public void cancel(Integer requestId, Integer borrowerId) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(400, "申请ID不合法");
        }
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null) {
            throw new BusinessException(404, BorrowRequestConstants.BORROW_REQUEST_NOT_EXIST);
        }
        if (!borrowRequest.getBorrowerId().equals(borrowerId)) {
            throw new BusinessException(403, "不能取消他人申请");
        }

        String originalStatus = borrowRequest.getStatus();

        if (!BorrowRequestConstants.PENDING.equals(originalStatus) && !BorrowRequestConstants.APPROVED.equals(originalStatus)) {
            throw new BusinessException(409, "当前申请状态不允许取消");
        }
        if (BorrowRequestConstants.APPROVED.equals(originalStatus)) {
            equipmentService.releaseReservation(borrowRequest.getDeviceId());
        }
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, originalStatus)
                .set(BorrowRequest::getStatus, BorrowRequestConstants.CANCEL);
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "取消失败");
        }
    }

    @Transactional
    public boolean cancelExpired(Integer requestId) {
        BorrowRequest borrowRequest = borrowRequestMapper.selectById(requestId);
        if (borrowRequest == null
                || !BorrowRequestConstants.APPROVED.equals(borrowRequest.getStatus())
                || borrowRequest.getApproveTime() == null) {
            return false;
        }

        LocalDateTime ddl = borrowRequest.getApproveTime()
                .toLocalDate()
                .plusDays(2)
                .atTime(12, 0);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (now.isBefore(ddl)) {
            return false;
        }
        equipmentService.releaseReservation(borrowRequest.getDeviceId());
        LambdaUpdateWrapper<BorrowRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BorrowRequest::getId, borrowRequest.getId())
                .eq(BorrowRequest::getStatus, BorrowRequestConstants.APPROVED)
                .eq(BorrowRequest::getApproveTime, borrowRequest.getApproveTime())
                .set(BorrowRequest::getStatus, BorrowRequestConstants.CANCEL);
        int result = borrowRequestMapper.update(null, updateWrapper);
        if (result != 1) {
            throw new BusinessException(409, "申请状态已变化，超时取消失败");
        }
        return true;
    }

    public IPage<BorrowRequestVO> listPending(Integer pageNum, Integer pageSize) {

        if (pageNum == null || pageNum < 1) {
            throw new BusinessException(400, "页码必须大于等于1");
        }

        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "每页条数必须在1到100之间");
        }

        Page<BorrowRequestVO> page = new Page<>(pageNum, pageSize);

        return borrowRequestMapper.selectPendingRequests(page);
    }

    public IPage<BorrowRequestVO> listReceived(Integer pageNum, Integer pageSize) {

        if (pageNum == null || pageNum < 1) {
            throw new BusinessException(400, "页码必须大于等于1");
        }

        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "每页条数必须在1到100之间");
        }

        Page<BorrowRequestVO> page = new Page<>(pageNum, pageSize);

        return borrowRequestMapper.selectReceivedRequests(page);
    }

}
