package io.gitee.chenshaogui.cloudauth.casm.service;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.SysKeyRotationBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysKeyRotationVo;

import java.util.List;

/**
 * 密钥轮换信息 服务层
 *
 * @author Lion Li
 */
public interface ISysKeyRotationService {

    /**
     * 分页查询密钥轮换信息列表
     *
     * @param bo    查询条件
     * @param pageQuery 分页参数
     * @return 密钥轮换信息分页列表
     */
    TableDataInfo<SysKeyRotationVo> selectPageConfigList(SysKeyRotationBo bo, PageQuery pageQuery);

    /**
     * 查询密钥轮换信息信息
     *
     * @param id 密钥轮换信息ID
     * @return 密钥轮换信息信息
     */
    SysKeyRotationVo selectById(Long id);



    /**
     * 查询密钥轮换信息列表
     *
     * @param bo 密钥轮换信息信息
     * @return 密钥轮换信息集合
     */
    List<SysKeyRotationVo> selectList(SysKeyRotationBo bo);

    /**
     * 新增密钥轮换信息
     *
     * @param bo 密钥轮换信息信息
     * @return 结果
     */
    Long insert(SysKeyRotationBo bo);

    /**
     * 修改密钥轮换信息
     *
     * @param bo 密钥轮换信息信息
     * @return 结果
     */
    Long update(SysKeyRotationBo bo);

    /**
     * 批量删除参数信息
     *
     * @param ids 需要删除的参数ID
     */
    void deleteByIds(List<Long> ids);

    /**
     * 重置参数缓存数据
     */
    void resetCache();

    /**
     * 校验参数键名是否唯一
     *
     * @param bo 参数信息
     * @return 结果
     */
    boolean checkKeyUnique(SysKeyRotationBo bo);

}
