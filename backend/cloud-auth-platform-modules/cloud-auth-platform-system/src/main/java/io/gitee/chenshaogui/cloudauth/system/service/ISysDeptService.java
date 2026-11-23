package io.gitee.chenshaogui.cloudauth.system.service;

import cn.hutool.core.lang.tree.Tree;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysDeptBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDeptVo;

import java.util.List;

/**
 * 部门管理 服务层
 *
 * @author Lion Li
 */
public interface ISysDeptService {

    /**
     * 分页查询部门管理数据
     *
     * @param dept      部门信息
     * @param pageQuery 分页对象
     * @return 部门信息集合
     */
    TableDataInfo<SysDeptVo> selectPageDeptList(SysDeptBo dept, PageQuery pageQuery);

    /**
     * 查询部门管理数据
     *
     * @param dept 部门信息
     * @return 部门信息集合
     */
    List<SysDeptVo> selectDeptList(SysDeptBo dept);

    /**
     * 查询部门树结构信息
     *
     * @param dept 部门信息
     * @return 部门树信息集合
     */
    List<Tree<Long>> selectDeptTreeList(SysDeptBo dept);

    /**
     * 构建前端所需要下拉树结构
     *
     * @param depts 部门列表
     * @return 下拉树结构列表
     */
    List<Tree<Long>> buildDeptTreeSelect(List<SysDeptVo> depts);

    /**
     * 根据角色ID查询部门树信息
     *
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    List<Long> selectDeptListByRoleId(Long roleId);

    /**
     * 根据部门ID查询信息
     *
     * @param deptId 部门ID
     * @return 部门信息
     */
    SysDeptVo selectDeptById(Long deptId);

    /**
     * 通过部门ID串查询部门
     *
     * @param deptIds 部门id串
     * @return 部门列表信息
     */
    List<SysDeptVo> selectDeptByIds(List<Long> deptIds);

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    String selectDeptNameByIds(String deptIds);

    /**
     * 通过部门ID查询部门编码
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门编码串逗号分隔（与 deptIds 一一对应，顺序一致）
     */
    String selectDeptCodeByIds(String deptIds);

    /**
     * 根据ID查询所有子部门数（正常状态）
     *
     * @param deptId 部门ID
     * @return 子部门数
     */
    long selectNormalChildrenDeptById(Long deptId);

    /**
     * 是否存在部门子节点
     *
     * @param deptId 部门ID
     * @return 结果
     */
    boolean hasChildByDeptId(Long deptId);

    /**
     * 查询部门是否存在用户
     *
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    boolean checkDeptExistUser(Long deptId);

    /**
     * 校验部门名称是否唯一
     *
     * @param dept 部门信息
     * @return 结果
     */
    boolean checkDeptNameUnique(SysDeptBo dept);

    /**
     * 校验部门编码（{@code dept_code}）是否唯一
     *
     * <p>约束：同租户（{@code tenant_id}）内 {@code dept_code} 不允许重复。
     * 与 {@link #checkDeptNameUnique(SysDeptBo)} 的差别：本校验以 {@code (tenant_id, dept_code)}
     * 作为唯一键（多租户隔离），且 {@code deptCode} 留空时跳过校验（多个空值不冲突）。</p>
     *
     * <p>该约束由 {@code sys_dept.uk_sys_dept_tenant_code} 唯一索引兜底，
     * 此方法仅用于在业务层给出友好错误提示，避免触发数据库唯一约束异常。</p>
     *
     * @param dept 部门信息
     * @return true 唯一 / false 重复；{@code deptCode} 为空时恒返回 true
     */
    boolean checkDeptCodeUnique(SysDeptBo dept);


    /**
     * 校验部门是否有数据权限
     *
     * @param deptId 部门id
     */
    void checkDeptDataScope(Long deptId);

    /**
     * 新增保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    int insertDept(SysDeptBo bo);

    /**
     * 修改保存部门信息
     *
     * @param bo 部门信息
     * @return 结果
     */
    int updateDept(SysDeptBo bo);

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    int deleteDeptById(Long deptId);

}
