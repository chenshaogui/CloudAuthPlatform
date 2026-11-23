package io.gitee.chenshaogui.cloudauth.system.service;


import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailLogBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailLogVo;

import java.util.List;

/**
 * 邮箱发送日志Service接口
 *
 * @author wdh
 * @since 2024-01-03
 */
public interface ISysMailLogService {

    /**
     * 查询邮箱发送日志列表
     */
    TableDataInfo<SysMailLogVo> getPageByParam(SysMailLogBo param, PageQuery pageQuery);

    /**
     * 查询邮箱发送日志列表
     */
    List<SysMailLogVo> getListByParam(SysMailLogBo param);


}
