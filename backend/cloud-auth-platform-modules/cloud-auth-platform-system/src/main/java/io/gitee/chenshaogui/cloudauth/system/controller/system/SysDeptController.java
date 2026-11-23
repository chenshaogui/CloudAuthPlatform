package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysDeptBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDeptVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDeptService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门信息
 *
 * <p>标准 CRUD 接口（list / getInfo / add / edit / remove / optionselect 等）通过
 * {@link SysDeptVo} / {@link SysDeptBo} 自动暴露 {@code deptCode}（部门编码）字段，
 * 客户端 SDK 通过 sa-token SSO 的 {@code getDeptCodeByIds} 消息按部门 ID 拉取。
 * Controller 端无需针对 {@code deptCode} 做任何额外处理。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dept")
public class SysDeptController extends BaseController {

    private final ISysDeptService deptService;
    private final ISysPostService postService;

    /**
     * 获取部门列表
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list")
    public R<List<SysDeptVo>> list(SysDeptBo dept) {
        List<SysDeptVo> depts = deptService.selectDeptList(dept);
        return R.ok(depts);
    }

    /**
     * 查询部门列表（排除节点）
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list/exclude/{deptId}")
    public R<List<SysDeptVo>> excludeChild(@PathVariable(value = "deptId", required = false) Long deptId) {
        List<SysDeptVo> depts = deptService.selectDeptList(new SysDeptBo());
        depts.removeIf(d -> d.getDeptId().equals(deptId)
            || StringUtils.splitList(d.getAncestors()).contains(Convert.toStr(deptId)));
        return R.ok(depts);
    }

    /**
     * 根据部门编号获取详细信息
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping(value = "/{deptId}")
    public R<SysDeptVo> getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return R.ok(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @SaCheckPermission("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDeptBo dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return R.fail("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        if (!deptService.checkDeptCodeUnique(dept)) {
            return R.fail("新增部门'" + dept.getDeptName() + "'失败，部门编码'" + dept.getDeptCode() + "'已存在");
        }
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改部门
     */
    @SaCheckPermission("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysDeptBo dept) {
        Long deptId = dept.getDeptId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept)) {
            return R.fail("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        } else if (!deptService.checkDeptCodeUnique(dept)) {
            return R.fail("修改部门'" + dept.getDeptName() + "'失败，部门编码'" + dept.getDeptCode() + "'已存在");
        } else if (dept.getParentId().equals(deptId)) {
            return R.fail("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        } else if (StringUtils.equals(SystemConstants.DISABLE, dept.getStatus())) {
            if (deptService.selectNormalChildrenDeptById(deptId) > 0) {
                return R.fail("该部门包含未停用的子部门!");
            } else if (deptService.checkDeptExistUser(deptId)) {
                return R.fail("该部门下存在已分配用户，不能禁用!");
            }
        }
        return toAjax(deptService.updateDept(dept));
    }

    /**
     * 删除部门
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public R<Void> remove(@PathVariable Long deptId) {
        if (StringUtils.equals(SystemConstants.ROOT_DEPT_ANCESTORS, String.valueOf(deptId))) {
            return R.warn("根部门不允许删除");
        }
        if (deptService.hasChildByDeptId(deptId)) {
            return R.warn("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            return R.warn("部门存在用户,不允许删除");
        }
        if (postService.countPostByDeptId(deptId) > 0) {
            return R.warn("部门存在岗位,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }

    /**
     * 获取部门选择框列表
     *
     * @param deptIds 部门ID串
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping("/optionselect")
    public R<List<SysDeptVo>> optionselect(@RequestParam(required = false) Long[] deptIds) {
        return R.ok(deptService.selectDeptByIds(deptIds == null ? null : List.of(deptIds)));
    }

}
