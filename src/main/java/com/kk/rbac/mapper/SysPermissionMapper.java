package com.kk.rbac.mapper;

import com.kk.rbac.vo.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper {
    List<PermissionVO> selectAllPermissions();
    @Select("SELECT EXISTS (" +
            "        SELECT 1" +
            "        FROM sys_permission" +
            "        WHERE id = #{permissionId}" +
            "    )")
    boolean existsById(Integer permissionId);
}