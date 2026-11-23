package io.gitee.chenshaogui.cloudauth.system.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailLogBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailLogVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMailLogMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMailLogService;
import lombok.RequiredArgsConstructor;
import org.apache.seata.common.result.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 邮箱发送日志Service业务层处理
 *
 * @author wdh
 * @since 2024-01-03
 */
@Service
@RequiredArgsConstructor
public class SysMailLogServiceImpl implements ISysMailLogService {

    private final SysMailLogMapper baseMapper;

    /**
     * 查询邮箱发送日志列表
     */
    @Override
    public TableDataInfo<SysMailLogVo> getPageByParam(SysMailLogBo param, PageQuery pageQuery) {
        Page<SysMailLogVo> page = baseMapper.selectVoPage(pageQuery.build(), Wrappers.lambdaQuery());
        return TableDataInfo.build(page);
    }

    /**
     * 查询邮箱发送日志列表
     */
    @Override
    public List<SysMailLogVo> getListByParam(SysMailLogBo param) {
        return baseMapper.selectListByParam(param);
    }

}
