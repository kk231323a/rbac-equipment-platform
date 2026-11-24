package com.kk.rbac.task;

import com.kk.rbac.dto.AuthStateSyncRecord;
import com.kk.rbac.mapper.AuthStateSyncTaskMapper;
import com.kk.rbac.service.UserAuthStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class AuthStateSyncRetryTask {
    @Autowired
    private AuthStateSyncTaskMapper authStateSyncTaskMapper;
    private int afterUserId;
    @Autowired
    private UserAuthStateService userAuthStateService;

    @Scheduled(fixedDelay = 30_000)
    public void retryPendingPasswordSync() {
        List<AuthStateSyncRecord> rows = authStateSyncTaskMapper.findBatch(afterUserId);

        for (AuthStateSyncRecord row : rows) {
            try {
                userAuthStateService.incrJwtVersion(row.userId(), row.targetTokenVersion());
                authStateSyncTaskMapper.delete(row.userId(), row.targetTokenVersion());
            } catch (Exception e) {
                log.warn("改密认证状态补偿失败，userId={}", row.userId(), e);
            }
        }

        afterUserId = rows.size() < 50 ? 0 : rows.get(rows.size() - 1).userId();
    }
}
