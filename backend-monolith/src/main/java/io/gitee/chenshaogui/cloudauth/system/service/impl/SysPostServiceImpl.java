package io.gitee.chenshaogui.cloudauth.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.SysPost;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserPost;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysPostBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysPostVo;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysDeptMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysPostMapper;
import io.gitee.chenshaogui.cloudauth.system.mapper.SysUserPostMapper;
import io.gitee.chenshaogui.cloudauth.system.event.PermissionChangeEvent;
import io.gitee.chenshaogui.cloudauth.system.service.ISysPostService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位信息 服务层处理
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysPostServiceImpl implements ISysPostService {

    private final SysPostMapper baseMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserPostMapper userPostMapper;

    /**
     * Spring 事件发布器（权限同步机制使用）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询岗位列表
     *
     * @param post      查询条件
     * @param pageQuery 分页参数
     * @return 岗位分页列表
     */
    @Override
    public TableDataInfo<SysPostVo> selectPagePostList(SysPostBo post, PageQuery pageQuery) {
        Page<SysPostVo> page = baseMapper.selectPagePostList(pageQuery.build(), buildQueryWrapper(post));
        return TableDataInfo.build(page);
    }

    /**
     * 查询岗位信息集合
     *
     * @param post 岗位信息
     * @return 岗位信息集合
     */
    @Override
    public List<SysPostVo> selectPostList(SysPostBo post) {
        return baseMapper.selectVoList(buildQueryWrapper(post));
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 岗位ID
     */
    @Override
    public List<SysPostVo> selectPostsByUserId(Long userId) {
        return baseMapper.selectPostsByUserId(userId);
    }

    /**
     * 批量查询多个用户的岗位名称列表（仅正常状态岗位）。
     * <p>sys_user_post 非租户表，sys_post 为租户表；先取 userId->postId 关联，
     * 再批量取 postId->postName，最后组装 userId->[postName...]。</p>
     */
    @Override
    public Map<Long, List<String>> selectPostNamesListByUserIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        // 1. 查 sys_user_post 得到 userId -> postId 关联
        List<SysUserPost> userPosts = userPostMapper.selectList(
            new LambdaQueryWrapper<SysUserPost>().in(SysUserPost::getUserId, userIds));
        if (CollUtil.isEmpty(userPosts)) {
            return Collections.emptyMap();
        }
        // 2. 查 sys_post 得到 postId -> postName（仅正常状态）
        List<Long> postIds = userPosts.stream().map(SysUserPost::getPostId).distinct().toList();
        List<SysPost> posts = baseMapper.selectList(new LambdaQueryWrapper<SysPost>()
            .select(SysPost::getPostId, SysPost::getPostName)
            .eq(SysPost::getStatus, SystemConstants.NORMAL)
            .in(SysPost::getPostId, postIds));
        Map<Long, String> postNameMap = StreamUtils.toMap(posts, SysPost::getPostId, SysPost::getPostName);
        // 3. 组装 userId -> [postName...]（停用/不存在的岗位自然跳过）
        Map<Long, List<String>> result = new HashMap<>();
        for (SysUserPost up : userPosts) {
            String name = postNameMap.get(up.getPostId());
            if (name != null) {
                result.computeIfAbsent(up.getUserId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    /**
     * 根据查询条件构建查询包装器
     *
     * @param bo 查询条件对象
     * @return 构建好的查询包装器
     */
    private LambdaQueryWrapper<SysPost> buildQueryWrapper(SysPostBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(bo.getPostCode()), SysPost::getPostCode, bo.getPostCode())
            .like(StringUtils.isNotBlank(bo.getPostCategory()), SysPost::getPostCategory, bo.getPostCategory())
            .like(StringUtils.isNotBlank(bo.getPostName()), SysPost::getPostName, bo.getPostName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SysPost::getStatus, bo.getStatus())
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                SysPost::getCreateTime, params.get("beginTime"), params.get("endTime"))
            .orderByAsc(SysPost::getPostSort);
        if (ObjectUtil.isNotNull(bo.getDeptId())) {
            //优先单部门搜索
            wrapper.eq(SysPost::getDeptId, bo.getDeptId());
        } else if (ObjectUtil.isNotNull(bo.getBelongDeptId())) {
            //部门树搜索
            wrapper.and(x -> {
                List<Long> deptIds = deptMapper.selectDeptAndChildById(bo.getBelongDeptId());
                x.in(SysPost::getDeptId, deptIds);
            });
        }
        return wrapper;
    }

    /**
     * 查询所有岗位
     *
     * @return 岗位列表
     */
    @Override
    public List<SysPostVo> selectPostAll() {
        return baseMapper.selectVoList(new QueryWrapper<>());
    }

    /**
     * 通过岗位ID查询岗位信息
     *
     * @param postId 岗位ID
     * @return 角色对象信息
     */
    @Override
    public SysPostVo selectPostById(Long postId) {
        return baseMapper.selectVoById(postId);
    }

    /**
     * 根据用户ID获取岗位选择框列表
     *
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    @Override
    public List<Long> selectPostListByUserId(Long userId) {
        List<SysPostVo> list = baseMapper.selectPostsByUserId(userId);
        return StreamUtils.toList(list, SysPostVo::getPostId);
    }

    /**
     * 通过岗位ID串查询岗位
     *
     * @param postIds 岗位id串
     * @return 岗位列表信息
     */
    @Override
    public List<SysPostVo> selectPostByIds(List<Long> postIds) {
        return baseMapper.selectVoList(new LambdaQueryWrapper<SysPost>()
            .select(SysPost::getPostId, SysPost::getPostName, SysPost::getPostCode)
            .eq(SysPost::getStatus, SystemConstants.NORMAL)
            .in(CollUtil.isNotEmpty(postIds), SysPost::getPostId, postIds));
    }

    /**
     * 校验岗位名称是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostNameUnique(SysPostBo post) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysPost>()
            .eq(SysPost::getPostName, post.getPostName())
            .eq(SysPost::getDeptId, post.getDeptId())
            .ne(ObjectUtil.isNotNull(post.getPostId()), SysPost::getPostId, post.getPostId()));
        return !exist;
    }

    /**
     * 校验岗位编码是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostCodeUnique(SysPostBo post) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<SysPost>()
            .eq(SysPost::getPostCode, post.getPostCode())
            .ne(ObjectUtil.isNotNull(post.getPostId()), SysPost::getPostId, post.getPostId()));
        return !exist;
    }

    /**
     * 通过岗位ID查询岗位使用数量
     *
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public long countUserPostById(Long postId) {
        return userPostMapper.selectCount(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getPostId, postId));
    }

    /**
     * 通过部门ID查询岗位使用数量
     *
     * @param deptId 部门id
     * @return 结果
     */
    @Override
    public long countPostByDeptId(Long deptId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<SysPost>().eq(SysPost::getDeptId, deptId));
    }

    /**
     * 删除岗位信息
     *
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int deletePostById(Long postId) {
        int rows = baseMapper.deleteById(postId);
        if (rows > 0) {
            publishPostChange(PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 批量删除岗位信息
     *
     * @param postIds 需要删除的岗位ID
     * @return 结果
     */
    @Override
    public int deletePostByIds(List<Long> postIds) {
        List<SysPost> list = baseMapper.selectByIds(postIds);
        for (SysPost post : list) {
            if (this.countUserPostById(post.getPostId()) > 0) {
                throw new ServiceException("{}已分配，不能删除!", post.getPostName());
            }
        }
        int rows = baseMapper.deleteByIds(postIds);
        if (rows > 0) {
            publishPostChange(PermissionChangeEvent.ChangeType.DELETE);
        }
        return rows;
    }

    /**
     * 新增保存岗位信息
     *
     * @param bo 岗位信息
     * @return 结果
     */
    @Override
    public int insertPost(SysPostBo bo) {
        SysPost post = MapstructUtils.convert(bo, SysPost.class);
        int rows = baseMapper.insert(post);
        if (rows > 0) {
            publishPostChange(PermissionChangeEvent.ChangeType.INSERT);
        }
        return rows;
    }

    /**
     * 修改保存岗位信息
     *
     * @param bo 岗位信息
     * @return 结果
     */
    @Override
    public int updatePost(SysPostBo bo) {
        SysPost post = MapstructUtils.convert(bo, SysPost.class);
        int rows = baseMapper.updateById(post);
        if (rows > 0) {
            publishPostChange(PermissionChangeEvent.ChangeType.UPDATE);
        }
        return rows;
    }

    /**
     * 发布岗位变更事件（走全网广播）
     *
     * <p>岗位信息会出现在登录用户上下文（{@code posts}）中，可能被业务方用于菜单/数据过滤，
     * 但"哪些用户属于该岗位"需要额外关联查询，因此统一广播，由客户端刷新登录用户</p>
     */
    private void publishPostChange(PermissionChangeEvent.ChangeType changeType) {
        try {
            eventPublisher.publishEvent(new PermissionChangeEvent(this, null, changeType));
        } catch (Exception e) {
            log.warn("岗位变更事件发布失败: {}", e.getMessage());
        }
    }
}
