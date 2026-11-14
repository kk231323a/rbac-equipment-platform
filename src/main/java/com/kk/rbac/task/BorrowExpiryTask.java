package com.kk.rbac.task;

import com.kk.rbac.service.BorrowExpiryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BorrowExpiryTask {

    @Autowired
    private BorrowExpiryService borrowExpiryService;

    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Shanghai")
    @EventListener(ApplicationReadyEvent.class)
    public void cancelExpiredRequests() {
        try {
            int count = borrowExpiryService.cancelExpiredRequests();
            log.info("超时取消任务执行完成，成功取消 {} 条申请", count);
        } catch (Exception exception) {
            log.error("超时取消任务执行失败", exception);
        }
    }
}