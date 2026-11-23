package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientRoleBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientRoleVo;

import java.util.Collection;
import java.util.List;

/**
 * 客户端角色关联管理Service接口
 *
 * @author sgchen
 */
public interface ISysClientRoleService {

    /**
     * 查询客户端角色关联管理列表
     */
    TableDataInfo<SysClientRoleVo> queryPageList(SysClientRoleBo bo, PageQuery pageQuery);

    /**
     * 查询客户端角色关联管理列表
     */
    List<SysClientRoleVo> queryList(SysClientRoleBo bo);

    /**
     * 新增客户端角色关联管理
     */
    Boolean insertByBo(SysClientRoleBo bo);

    /**
     * 修改客户端角色关联管理
     */
    Boolean updateByBo(SysClientRoleBo bo);

    Boolean insertOrUpdateByBo(SysClientRoleBo bo);

    /**
     * 批量删除客户端角色管理
     */
    Boolean deleteWithValidByIds(Collection<Long> ids);

    Boolean deleteByRoleId(Long roleId);

    SysClientRoleVo getInfoByRoleId(Long roleId);

    /**
     * 给客户端绑定角色（先删后加，保证幂等）
     */
    boolean bindRolesToClient(String clientId, List<Long> roleIds);

    /**
     * 根据客户端ID查询关联的角色列表
     */
    List<Long> getRoleIdsByClientId(Long clientId);
}
