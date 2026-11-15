package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.entity.BorrowRequest;
import com.kk.rbac.vo.BorrowRequestVO;
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
    IPage<BorrowRequestVO> selectPendingRequests(
            Page<BorrowRequestVO> page
    );
    IPage<BorrowRequestVO> selectReceivedRequests(
            Page<BorrowRequestVO> page
    );
}