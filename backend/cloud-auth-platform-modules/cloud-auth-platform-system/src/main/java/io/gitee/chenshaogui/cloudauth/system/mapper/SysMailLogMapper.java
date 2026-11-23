package io.gitee.chenshaogui.cloudauth.system.mapper;

import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysMailLog;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMailLogBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMailLogVo;

import java.util.List;

/**
 * 邮箱发送日志Mapper接口
 *
 * @author wdh
 * @since 2024-01-03
 */
public interface SysMailLogMapper extends BaseMapperPlus<SysMailLog, SysMailLogVo> {

    List<SysMailLogVo> selectListByParam(SysMailLogBo param);

    int batchInsert(List<SysMailLog> list);

}
