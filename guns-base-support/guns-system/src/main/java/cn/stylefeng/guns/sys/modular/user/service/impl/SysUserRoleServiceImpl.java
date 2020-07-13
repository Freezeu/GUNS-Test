package cn.stylefeng.guns.sys.modular.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.stylefeng.guns.sys.modular.role.service.SysRoleService;
import cn.stylefeng.guns.sys.modular.user.entity.SysUserRole;
import cn.stylefeng.guns.sys.modular.user.mapper.SysUserRoleMapper;
import cn.stylefeng.guns.sys.modular.user.param.SysUserParam;
import cn.stylefeng.guns.sys.modular.user.service.SysUserRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 系统用户角色service接口实现类
 *
 * @author xuyuxiang
 * @date 2020/3/13 15:48
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Resource
    private SysRoleService sysRoleService;

    /**
     * 获取用户的角色id集合
     *
     * @author xuyuxiang
     * @date 2020/3/20 22:33
     */
    @Override
    public List<Long> getUserRoleIdList(Long userId) {
        List<Long> roleIdList = CollectionUtil.newArrayList();
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        this.list(queryWrapper).forEach(sysUserRole -> roleIdList.add(sysUserRole.getRoleId()));
        return roleIdList;
    }

    /**
     * 授权角色
     *
     * @author xuyuxiang
     * @date 2020/3/28 16:57
     */
    @Override
    public void grantRole(SysUserParam sysUserParam) {
        Long userId = sysUserParam.getId();
        //删除所拥有角色
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        this.remove(queryWrapper);
        //授权角色
        sysUserParam.getGrantRoleIdList().forEach(roleId -> {
            SysUserRole sysUserRole = new SysUserRole();
            sysUserRole.setUserId(userId);
            sysUserRole.setRoleId(roleId);
            this.save(sysUserRole);
        });
    }

    /**
     * 获取用户的数据范围（组织机构id集合）
     *
     * @author xuyuxiang
     * @date 2020/4/5 17:32
     */
    @Override
    public List<Long> getUserDataScopeIdList(Long userId, Long orgId) {
        List<Long> roleIdList = CollectionUtil.newArrayList();
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        this.list(queryWrapper).forEach(sysUserRole -> roleIdList.add(sysUserRole.getRoleId()));
        if(ObjectUtil.isNotEmpty(roleIdList)) {
            return sysRoleService.getUserDataScopeIdList(roleIdList, orgId);
        }
        return CollectionUtil.newArrayList();
    }

    /**
     * 根据角色id删除对应的用户-角色表关联信息
     *
     * @author xuyuxiang
     * @date 2020/6/28 14:23
     */
    @Override
    public void deleteUserRoleListByRoleId(Long roleId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getRoleId, roleId);
        this.remove(queryWrapper);
    }

    /**
     * 根据用户id删除对应的用户-角色表关联信息
     *
     * @author xuyuxiang
     * @date 2020/6/28 14:53
     */
    @Override
    public void deleteUserRoleListByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, userId);
        this.remove(queryWrapper);
    }
}