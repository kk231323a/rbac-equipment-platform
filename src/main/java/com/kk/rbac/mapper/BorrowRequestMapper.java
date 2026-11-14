package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kk.rbac.entity.BorrowRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BorrowRequestMapper extends BaseMapper<BorrowRequest> {
    List<Integer> selectExpiredIds(
            @Param("approveTimeBefore") LocalDateTime approveTimeBefore,
            @Param("afterId") Integer afterId
    );
}