package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kk.rbac.entity.SysRole;
import com.kk.rbac.vo.RoleVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    List<RoleVO> selectRolesByUserId(Integer userId);
}