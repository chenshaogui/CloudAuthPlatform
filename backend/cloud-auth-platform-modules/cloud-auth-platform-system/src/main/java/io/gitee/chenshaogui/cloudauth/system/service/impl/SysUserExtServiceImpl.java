package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserExt;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserExtMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserExtService;
import org.springframework.stereotype.Service;

/**
 * 用户扩展信息 业务层实现
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysUserExtServiceImpl implements ISysUserExtService {

    private final SysUserExtMapper baseMapper;

    /**
     * 根据 userId 查询扩展信息
     */
    @Override
    public SysUserExt queryByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return baseMapper.selectById(userId);
    }

    /**
     * 新增或更新扩展信息（按 userId 主键幂等）
     */
    @Override
    public boolean saveOrUpdate(SysUserExt ext) {
        if (ext == null || ext.getUserId() == null) {
            log.warn("[SysUserExtService] saveOrUpdate 失败：userId 不能为空");
            return false;
        }
        SysUserExt exist = baseMapper.selectById(ext.getUserId());
        if (exist == null) {
            return baseMapper.insert(ext) > 0;
        }
        return baseMapper.updateById(ext) > 0;
    }

    /**
     * 根据 userId 删除扩展信息
     */
    @Override
    public boolean deleteByUserId(Long userId) {
        if (userId == null) {
            return false;
        }
        return baseMapper.delete(new LambdaQueryWrapper<SysUserExt>().eq(SysUserExt::getUserId, userId)) > 0;
    }

}