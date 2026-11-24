package com.kk.rbac.mapper;

import com.kk.rbac.dto.AuthStateSyncRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AuthStateSyncTaskMapper {

    @Insert("""
            INSERT INTO auth_state_sync_task (user_id, target_token_version)
            VALUES (#{userId}, #{targetVersion})
            ON DUPLICATE KEY UPDATE target_token_version = #{targetVersion}
            """)
    void save(@Param("userId") Integer userId,
              @Param("targetVersion") long targetVersion);

    @Delete("""
            DELETE FROM auth_state_sync_task
            WHERE user_id = #{userId}
              AND target_token_version = #{targetVersion}
            """)
    void delete(@Param("userId") Integer userId,
                @Param("targetVersion") long targetVersion);
    @Select("""
        SELECT user_id AS userId,
               target_token_version AS targetTokenVersion
        FROM auth_state_sync_task
        WHERE user_id > #{afterUserId}
        ORDER BY user_id
        LIMIT 50
        """)
    List<AuthStateSyncRecord> findBatch(int afterUserId);
}