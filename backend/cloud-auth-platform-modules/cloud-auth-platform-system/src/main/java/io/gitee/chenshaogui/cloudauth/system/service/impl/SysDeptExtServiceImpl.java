package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDeptExt;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysDeptExtMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDeptExtService;
import org.springframework.stereotype.Service;

/**
 * 部门扩展信息 业务层实现
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDeptExtServiceImpl implements ISysDeptExtService {

    private final SysDeptExtMapper baseMapper;

    /**
     * 根据 deptId 查询扩展信息
     */
    @Override
    public SysDeptExt queryByDeptId(Long deptId) {
        if (deptId == null) {
            return null;
        }
        return baseMapper.selectById(deptId);
    }

    /**
     * 新增或更新扩展信息（按 deptId 主键幂等）
     */
    @Override
    public boolean saveOrUpdate(SysDeptExt ext) {
        if (ext == null || ext.getDeptId() == null) {
            log.warn("[SysDeptExtService] saveOrUpdate 失败：deptId 不能为空");
            return false;
        }
        SysDeptExt exist = baseMapper.selectById(ext.getDeptId());
        if (exist == null) {
            return baseMapper.insert(ext) > 0;
        }
        return baseMapper.updateById(ext) > 0;
    }

    /**
     * 根据 deptId 删除扩展信息
     */
    @Override
    public boolean deleteByDeptId(Long deptId) {
        if (deptId == null) {
            return false;
        }
        return baseMapper.delete(new LambdaQueryWrapper<SysDeptExt>().eq(SysDeptExt::getDeptId, deptId)) > 0;
    }

}