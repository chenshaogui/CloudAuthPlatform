package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.system.domain.SysUserExt;

/**
 * 用户扩展信息 服务层
 *
 * <p>与 sys_user 1:1 关联，存储来自 ln_pro sys_sec_user 的独有字段
 * （身份证、企业信息、从业资格证、CA 证书、桌面配置等）。</p>
 *
 * @author Lion Li
 */
public interface ISysUserExtService {

    /**
     * 根据 userId 查询扩展信息
     *
     * @param userId 用户ID（sys_user.user_id）
     * @return 扩展信息，若不存在返回 null
     */
    SysUserExt queryByUserId(Long userId);

    /**
     * 新增或更新扩展信息（按 userId 主键幂等）
     *
     * @param ext 扩展信息
     * @return true 成功 / false 失败
     */
    boolean saveOrUpdate(SysUserExt ext);

    /**
     * 根据 userId 删除扩展信息
     *
     * @param userId 用户ID
     * @return true 成功 / false 失败
     */
    boolean deleteByUserId(Long userId);

}