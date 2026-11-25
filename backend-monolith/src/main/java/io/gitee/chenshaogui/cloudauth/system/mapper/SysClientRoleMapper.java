package io.gitee.chenshaogui.cloudauth.system.mapper;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClientRole;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientRoleVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户端角色管理Mapper接口
 *
 * @author Michelle.Chung
 */
public interface SysClientRoleMapper extends BaseMapperPlus<SysClientRole, SysClientRoleVo> {

    /**
     * 查询用户所属角色所拥有的客户端权限（角色路径）
     *
     * @param userId 用户ID
     * @return 客户端ID列表
     */
    @Select("""
            select scr.client_id from sys_client_role scr
            where scr.role_id in (
                select sur.role_id from sys_user_role sur
                    left join sys_role sr on sr.role_id = sur.role_id
                where sur.user_id = #{userId} and sr.status = '0'
            )
            """)
    List<String> selectClientIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户被授予客户端管理员（{clientId}-admin）的角色ID
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Select("""
            SELECT DISTINCT sr.role_id FROM sys_user_role sur
             JOIN sys_role sr ON sr.role_id = sur.role_id
             JOIN sys_client_role scr ON scr.role_id = sur.role_id
            WHERE sur.user_id = #{userId}
              AND sr.status = '0'
              AND sr.role_key = CONCAT(scr.client_id, '-admin')
            """)
    List<Long> selectAdminRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户作为客户端管理员（{clientId}-admin）的客户端ID集合
     *
     * <p>用于菜单归属校验：判断用户对某个应用是否有新增/分配权限</p>
     *
     * @param userId 用户ID
     * @return 客户端ID列表
     */
    @Select("""
            SELECT DISTINCT scr.client_id FROM sys_user_role sur
             JOIN sys_role sr ON sr.role_id = sur.role_id
             JOIN sys_client_role scr ON scr.role_id = sur.role_id
            WHERE sur.user_id = #{userId}
              AND sr.status = '0'
              AND sr.role_key = CONCAT(scr.client_id, '-admin')
            """)
    List<String> selectAdminClientIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询客户端对应的管理员角色（{clientId}-admin）ID列表
     *
     * <p>用于应用菜单新增后自动授权给该应用的客户端管理员角色</p>
     *
     * @param clientId 客户端ID
     * @return 角色ID列表
     */
    @Select("""
            SELECT DISTINCT sr.role_id FROM sys_role sr
             JOIN sys_client_role scr ON scr.role_id = sr.role_id
            WHERE scr.client_id = #{clientId}
              AND sr.status = '0'
              AND sr.role_key = CONCAT(scr.client_id, '-admin')
            """)
    List<Long> selectAdminRoleIdsByClientId(@Param("clientId") String clientId);

    /**
     * 统计客户端下已绑定的正常状态角色数量
     */
    Long countRoleCountByClientIds(@Param("clientIdList") List<String> clientIdList);

}
