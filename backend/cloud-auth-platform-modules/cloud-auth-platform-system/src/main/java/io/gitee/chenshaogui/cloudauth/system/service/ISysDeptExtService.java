package io.gitee.chenshaogui.cloudauth.system.service;

import io.gitee.chenshaogui.cloudauth.system.domain.SysDeptExt;

/**
 * 部门扩展信息 服务层
 *
 * <p>与 sys_dept 1:1 关联，存储来自 ln_pro sys_sec_department 的独有字段
 * （电话、邮编、地址、URL、值班、负责人描述等）。</p>
 *
 * @author Lion Li
 */
public interface ISysDeptExtService {

    /**
     * 根据 deptId 查询扩展信息
     *
     * @param deptId 部门ID（sys_dept.dept_id）
     * @return 扩展信息，若不存在返回 null
     */
    SysDeptExt queryByDeptId(Long deptId);

    /**
     * 新增或更新扩展信息（按 deptId 主键幂等）
     *
     * @param ext 扩展信息
     * @return true 成功 / false 失败
     */
    boolean saveOrUpdate(SysDeptExt ext);

    /**
     * 根据 deptId 删除扩展信息
     *
     * @param deptId 部门ID
     * @return true 成功 / false 失败
     */
    boolean deleteByDeptId(Long deptId);

}