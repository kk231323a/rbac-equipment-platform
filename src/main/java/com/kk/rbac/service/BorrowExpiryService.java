package com.kk.rbac.service;

import com.kk.rbac.common.exception.BusinessException;
import com.kk.rbac.mapper.BorrowRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class BorrowExpiryService {
    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    @Autowired
    private BorrowRequestService borrowRequestService;

    private LocalDateTime calculateApproveTimeBefore(LocalDateTime now) {
        LocalDateTime before = now.toLocalDate()
                .minusDays(1)
                .atStartOfDay();

        if (now.toLocalTime().isBefore(LocalTime.NOON)) {
            before = before.minusDays(1);
        }

        return before;
    }
    public int cancelExpiredRequests() {
        LocalDateTime now = LocalDateTime.now(
                ZoneId.of("Asia/Shanghai")
        );
        LocalDateTime approveTimeBefore = calculateApproveTimeBefore(now);

        int afterId = 0;
        int cancelledCount = 0;

        while (true) {
            List<Integer> ids = borrowRequestMapper.selectExpiredIds(
                    approveTimeBefore, afterId
            );

            if (ids.isEmpty()) {
                return cancelledCount;
            }

            for (Integer requestId : ids) {
                try {
                    if (borrowRequestService.cancelExpired(requestId)) {
                        cancelledCount++;
                    }
                } catch (RuntimeException exception) {
                    log.warn("超时取消未完成，申请ID：{}，原因：{}",
                            requestId, exception instanceof BusinessException
                                    ? exception.getMessage() : exception.getClass().getSimpleName());
                }

                afterId = requestId;
            }
        }
    }
}
