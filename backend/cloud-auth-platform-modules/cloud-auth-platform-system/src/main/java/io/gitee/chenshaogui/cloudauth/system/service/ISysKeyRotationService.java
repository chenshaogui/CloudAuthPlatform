package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysKeyRotationBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyRotationVo;

/**
 * 密钥轮换记录管理Service接口
 *
 * @author JLL
 */
public interface ISysKeyRotationService {

    /**
     * 查询密钥轮换记录管理
     */
    SysKeyRotationVo viewById(Long id);

    /**
     * 查询密钥轮换记录管理列表
     */
    TableDataInfo<SysKeyRotationVo> queryPageList(SysKeyRotationBo bo, PageQuery pageQuery);


}
