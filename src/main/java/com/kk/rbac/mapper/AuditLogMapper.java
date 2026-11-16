package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kk.rbac.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}