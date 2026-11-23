package io.gitee.chenshaogui.cloudauth.casm.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.casm.domain.SysKeyRotation;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.SysKeyRotationBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysKeyRotationVo;
import io.gitee.chenshaogui.cloudauth.casm.mapper.SysKeyRotationMapper;
import io.gitee.chenshaogui.cloudauth.casm.service.ISysKeyRotationService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.CacheNames;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.CacheUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 参数配置 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysKeyRotationServiceImpl implements ISysKeyRotationService {

    private final SysKeyRotationMapper baseMapper;

    /**
     * 分页查询参数配置列表
     *
     * @param bo    查询条件
     * @param pageQuery 分页参数
     * @return 参数配置分页列表
     */
    @Override
    public TableDataInfo<SysKeyRotationVo> selectPageConfigList(SysKeyRotationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysKeyRotation> lqw = buildQueryWrapper(bo);
        Page<SysKeyRotationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysKeyRotationVo selectById(Long configId) {
        return baseMapper.selectVoById(configId);
    }

    /**
     * 查询参数配置列表
     *
     * @param bo 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysKeyRotationVo> selectList(SysKeyRotationBo bo) {
        LambdaQueryWrapper<SysKeyRotation> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<SysKeyRotation> buildQueryWrapper(SysKeyRotationBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysKeyRotation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getClientId()), SysKeyRotation::getId, bo.getId());
        lqw.between(params.get("beginTime") != null && params.get("endTime") != null,
            SysKeyRotation::getCreateTime, params.get("beginTime"), params.get("endTime"));
        lqw.orderByAsc(SysKeyRotation::getId);
        return lqw;
    }

    /**
     * 新增参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_KEY_ROTATION, key = "#bo.id")
    @Override
    public Long insert(SysKeyRotationBo bo) {
        SysKeyRotation data = MapstructUtils.convert(bo, SysKeyRotation.class);
        int row = baseMapper.insert(data);
        if (row > 0) {
            return data.getId();
        }
        throw new ServiceException("操作失败");
    }

    /**
     * 修改参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_KEY_ROTATION, key = "#bo.id")
    @Override
    public Long update(SysKeyRotationBo bo) {
        int row = 0;
        SysKeyRotation dd = MapstructUtils.convert(bo, SysKeyRotation.class);
        if (dd.getId() != null) {
            SysKeyRotation temp = baseMapper.selectById(dd.getId());
            if (!ObjectUtil.equals(temp.getId(), dd.getId())) {
                CacheUtils.evict(CacheNames.SYS_CONFIG, temp.getId());
            }
            row = baseMapper.updateById(dd);
        } else {
            CacheUtils.evict(CacheNames.SYS_CONFIG, dd.getId());
            row = baseMapper.update(dd, new LambdaQueryWrapper<SysKeyRotation>()
                .eq(SysKeyRotation::getId, bo.getId()));
        }
        if (row > 0) {
            return bo.getId();
        }
        throw new ServiceException("操作失败");
    }

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteByIds(List<Long> configIds) {
        List<SysKeyRotation> list = baseMapper.selectByIds(configIds);
        list.forEach(d -> {
            if (StringUtils.equals(SystemConstants.YES, d.getRotationTrigger())) {
                throw new ServiceException("内置参数【{}】不能删除", d.getNewKeyId());
            }
            CacheUtils.evict(CacheNames.SYS_CONFIG, d.getNewKeyId());
        });
        baseMapper.deleteByIds(configIds);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetCache() {
        CacheUtils.clear(CacheNames.SYS_KEY_ROTATION);
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkKeyUnique(SysKeyRotationBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysKeyRotation>()
            .eq(SysKeyRotation::getId, bo.getId())
            .ne(ObjectUtil.isNotNull(bo.getId()), SysKeyRotation::getId, bo.getId()));
        return !exist;
    }

}
