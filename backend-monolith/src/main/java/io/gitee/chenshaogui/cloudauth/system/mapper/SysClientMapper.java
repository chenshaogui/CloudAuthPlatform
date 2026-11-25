package io.gitee.chenshaogui.cloudauth.system.mapper;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysClient;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysClientVo;

/**
 * 授权管理Mapper接口
 *
 * @author Michelle.Chung
 */
public interface SysClientMapper extends BaseMapperPlus<SysClient, SysClientVo> {

    default String buildMenuIdByUserSql(Long userId) {
        return """
                select sc.menu_id from sys_client sc
                where sc.del_flag=0 and
                   exists (
                   select 1 from sys_user_client_config succ
                   where succ.client_id = sc.client_id and succ.user_id = %d
                   )
            """.formatted(userId);
    }

    default String getMenuIdListSql() {
        return """
                select sc.menu_id from sys_client sc where sc.del_flag=0
                and sc.menu_id is not null and sc.menu_id !=''
            """;
    }

}
