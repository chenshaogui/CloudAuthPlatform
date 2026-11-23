package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysClientBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyInfoBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;

import java.util.List;

/**
 * 密钥信息管理Service接口
 *
 * @author JLL
 */
public interface ISysKeyInfoService {

    /**
     * 查询密钥信息管理
     */
    SysKeyInfoVo viewById(Long id);

    /**
     * 查询密钥信息信息基于密钥信息id
     */
    SysKeyInfoVo applyKey(SysKeyInfoBo bo);

    /**
     * 查询密钥信息管理列表
     */
    TableDataInfo<SysKeyInfoVo> queryPageList(SysKeyInfoBo bo, PageQuery pageQuery);

    /**
     * 查询密钥信息管理列表
     */
    List<SysKeyInfoVo> queryList(SysKeyInfoBo bo);

    SysKeyInfoVo rotationKey(Long id);

    /**
     * 新增密钥信息管理
     */
    Boolean distroyKey(Long id);


}
