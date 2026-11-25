package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.util.ReUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailTemplate;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailTemplateBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailTemplateVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMailTemplateMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 邮箱模板Service业务层处理
 *
 * @author wdh
 * @since 2024-01-02
 */
@Service
@RequiredArgsConstructor
public class SysMailTemplateServiceImpl implements ISysMailTemplateService {

    private final SysMailTemplateMapper baseMapper;

    private final IdentifierGenerator identifierGenerator;

    @Override
    public boolean add(SysMailTemplate template) {
        template.setTypeParams(parseTemplateContentParams(template.getContent()));
        template.setId((Long) identifierGenerator.nextId(null));
        return baseMapper.insertOrUpdate(template);
    }

    @Override
    public boolean edit(SysMailTemplate template) {
        template.setTypeParams(parseTemplateContentParams(template.getContent()));
        return baseMapper.insertOrUpdate(template);
    }

    private String parseTemplateContentParams(String content) {
        return ReUtil.findAllGroup1(Pattern.compile("\\{(.*?)}"), content).toString();
    }

    /**
     * 查询邮箱模板列表
     */
    @Override
    public TableDataInfo<SysMailTemplateVo> getPageByParam(SysMailTemplateBo param, PageQuery pageQuery) {
        Page<SysMailTemplateVo> page = baseMapper.selectVoPage(pageQuery.build(), Wrappers.lambdaQuery());
        return TableDataInfo.build(page);
    }

    /**
     * 查询邮箱模板列表
     */
    @Override
    public List<SysMailTemplateVo> getListByParam(SysMailTemplateBo param) {
        return baseMapper.selectListByParam(param);
    }

}
