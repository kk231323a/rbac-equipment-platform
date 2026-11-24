package com.kk.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kk.rbac.entity.SysUser;
import com.kk.rbac.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    List<String> selectPermissionCodesByUserId(Integer userId);

    Page<UserVO> selectAllUser(Page<UserVO> page);
    List<SysUser> selectUsersByRoleId(Integer roleId);

}
