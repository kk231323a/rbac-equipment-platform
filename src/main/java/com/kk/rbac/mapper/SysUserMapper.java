package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kk.rbac.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    List<String> selectPermissionCodesByUserId(Integer userId);

}
