package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;

import java.util.Collection;
import java.util.List;

/**
 * 客户端管理Service接口
 *
 * @author Michelle.Chung
 */
public interface ISysClientService {

    /**
     * 查询客户端管理
     */
    SysClientVo queryById(Long id);

    /**
     * 查询客户端信息基于客户端id
     */
    SysClientVo queryByClientId(String clientId);

    /**
     * 查询客户端信息基于菜单id
     */
    List<SysClientVo> queryByMenuIds(List<Long> menuIdList);

    /**
     * 判断应用菜单是否被激活的客户端绑定（用于菜单删除/修改时的客户端引用保护）
     *
     * @param menuId 应用菜单ID（menu_type='A'）
     * @return true=被未删除的客户端绑定，禁止后续操作
     */
    boolean existsActiveClientByMenuId(Long menuId);

    /**
     * 查询客户端管理列表
     */
    TableDataInfo<SysClientVo> queryPageList(SysClientBo bo, PageQuery pageQuery);

    /**
     * 查询客户端管理列表
     */
    List<SysClientVo> queryList(SysClientBo bo);

    List<SysClientVo> queryListByUserId(Long userId);

    /**
     * 新增客户端管理
     */
    Boolean insertByBo(SysClientBo bo);

    /**
     * 修改客户端管理
     */
    Boolean updateByBo(SysClientBo bo);

    /**
     * 修改状态
     */
    int updateClientStatus(String clientId, String status);

    /**
     * 校验并批量删除客户端管理信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
