package io.gitee.chenshaogui.cloudauth.system.dubbo;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysDeptBo;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteDeptService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteDeptVo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysDept;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDeptVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysDeptMapper;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDeptService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 部门服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteDeptServiceImpl implements RemoteDeptService {

    private final ISysDeptService deptService;
    private final SysDeptMapper deptMapper;

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    @Override
    public String selectDeptNameByIds(String deptIds) {
        return deptService.selectDeptNameByIds(deptIds);
    }

    /**
     * 通过部门ID查询部门编码
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门编码串逗号分隔（与 deptIds 一一对应，顺序一致）
     */
    @Override
    public String selectDeptCodeByIds(String deptIds) {
        return deptService.selectDeptCodeByIds(deptIds);
    }

    /**
     * 根据部门ID查询部门负责人
     *
     * @param deptId 部门ID，用于指定需要查询的部门
     * @return 返回该部门的负责人ID
     */
    @Override
    public Long selectDeptLeaderById(Long deptId) {
        SysDeptVo vo = deptService.selectDeptById(deptId);
        return vo.getLeader();
    }

    /**
     * 查询部门
     *
     * @return 部门列表
     */
    @Override
    public List<RemoteDeptVo> selectDeptsByList() {
        List<SysDeptVo> list = deptMapper.selectDeptList(new LambdaQueryWrapper<SysDept>()
            .select(SysDept::getDeptId, SysDept::getDeptName, SysDept::getDeptCode, SysDept::getParentId)
            .eq(SysDept::getStatus, SystemConstants.NORMAL));
        return BeanUtil.copyToList(list, RemoteDeptVo.class);
    }

    /**
     * 查询子部门信息
     *
     * @return 部门列表
     */
    @Override
    public List<RemoteDeptVo> selectDeptsByBelongId(Long deptId) {
        SysDeptBo bo = new SysDeptBo();
        bo.setBelongDeptId(deptId);
        List<SysDeptVo> list = deptService.selectDeptList(bo);
        return BeanUtil.copyToList(list, RemoteDeptVo.class);
    }

    /**
     * 根据部门 ID 列表查询部门名称映射关系
     *
     * @param deptIds 部门 ID 列表
     * @return Map，其中 key 为部门 ID，value 为对应的部门名称
     */
    @Override
    public Map<Long, String> selectDeptNamesByIds(List<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyMap();
        }
        List<SysDept> list = deptMapper.selectList(
            new LambdaQueryWrapper<SysDept>()
                .select(SysDept::getDeptId, SysDept::getDeptName)
                .in(SysDept::getDeptId, deptIds)
        );
        return StreamUtils.toMap(list, SysDept::getDeptId, SysDept::getDeptName);
    }

}
