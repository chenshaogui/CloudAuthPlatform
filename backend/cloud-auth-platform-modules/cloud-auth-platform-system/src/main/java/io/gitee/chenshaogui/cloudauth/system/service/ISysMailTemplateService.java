package io.gitee.chenshaogui.cloudauth.system.service;



import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailTemplateBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailTemplateVo;
import org.apache.seata.common.result.PageResult;

import java.util.List;

/**
 * 邮箱模板Service接口
 *
 * @author wdh
 * @since 2024-01-02
 */
public interface ISysMailTemplateService {

    boolean add(SysMailTemplate template);

    boolean edit(SysMailTemplate template);

    /**
     * 查询邮箱模板列表
     */
    TableDataInfo<SysMailTemplateVo> getPageByParam(SysMailTemplateBo param, PageQuery pageQuery);

    /**
     * 查询邮箱模板列表
     */
    List<SysMailTemplateVo> getListByParam(SysMailTemplateBo param);

}
