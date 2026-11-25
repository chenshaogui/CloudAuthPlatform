package io.gitee.chenshaogui.cloudauth.system.mapper;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysUserClientConfig;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserClientConfigVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户与客户端关联表 数据层
 *
 * @author sgchen
 */
public interface SysUserClientConfigMapper extends BaseMapperPlus<SysUserClientConfig, SysUserClientConfigVo> {

    /**
     * 查询与当前用户同客户端授权范围的用户ID列表
     *
     * @param userId 用户ID
     * @return 用户ID列表
     */
    @Select("""
            select succ.user_id from sys_user_client_config succ
            where succ.client_id in (
                select scr.client_id from sys_client_role scr
                where scr.role_id in (
                    select sur.role_id from sys_user_role sur
                        left join sys_role sr on sr.role_id = sur.role_id
                    where sur.user_id = #{userId} and sr.status = '0'
                )
            )
            """)
    List<Long> selectUserIdsByUserRole(@Param("userId") Long userId);

    /**
     * 查询绑定指定客户端的用户ID列表
     *
     * @param clientId 客户端ID
     * @return 用户ID列表
     */
    @Select("select succ.user_id from sys_user_client_config succ where succ.client_id = #{clientId}")
    List<Long> selectUserIdsByClient(@Param("clientId") String clientId);

    /**
     * 查询用户被直接授权（用户-客户端配置路径）的客户端ID列表
     *
     * @param userId 用户ID
     * @return 客户端ID列表
     */
    @Select("select succ.client_id from sys_user_client_config succ where succ.user_id = #{userId}")
    List<String> selectClientIdsByUserId(@Param("userId") Long userId);

}
