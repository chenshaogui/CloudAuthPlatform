package io.gitee.chenshaogui.cloudauth.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMenuExt;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysMenuExtMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuExtService;
import org.springframework.stereotype.Service;

/**
 * 菜单扩展信息 业务层实现
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysMenuExtServiceImpl implements ISysMenuExtService {

    private final SysMenuExtMapper baseMapper;

    /**
     * 根据 menuId 查询扩展信息
     */
    @Override
    public SysMenuExt queryByMenuId(Long menuId) {
        if (menuId == null) {
            return null;
        }
        return baseMapper.selectById(menuId);
    }

    /**
     * 新增或更新扩展信息（按 menuId 主键幂等）
     */
    @Override
    public boolean saveOrUpdate(SysMenuExt ext) {
        if (ext == null || ext.getMenuId() == null) {
            log.warn("[SysMenuExtService] saveOrUpdate 失败：menuId 不能为空");
            return false;
        }
        SysMenuExt exist = baseMapper.selectById(ext.getMenuId());
        if (exist == null) {
            return baseMapper.insert(ext) > 0;
        }
        return baseMapper.updateById(ext) > 0;
    }

    /**
     * 根据 menuId 删除扩展信息
     */
    @Override
    public boolean deleteByMenuId(Long menuId) {
        if (menuId == null) {
            return false;
        }
        return baseMapper.delete(new LambdaQueryWrapper<SysMenuExt>().eq(SysMenuExt::getMenuId, menuId)) > 0;
    }

}