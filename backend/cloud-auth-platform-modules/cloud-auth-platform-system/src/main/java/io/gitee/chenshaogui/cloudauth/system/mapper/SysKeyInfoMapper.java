package io.gitee.chenshaogui.cloudauth.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.mapper.BaseMapperPlus;
import io.gitee.chenshaogui.cloudauth.system.domain.SysKeyInfo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysKeyInfoVo;
import org.apache.ibatis.annotations.Param;

/**
 * 密钥管理Mapper接口
 *
 * @author JLL
 */
public interface SysKeyInfoMapper extends BaseMapperPlus<SysKeyInfo, SysKeyInfoVo> {

    Page<SysKeyInfoVo> selectJoinVoList(@Param("page") Page<SysKeyInfo> page, @Param(Constants.WRAPPER) Wrapper<SysKeyInfo> queryWrapper);
}
