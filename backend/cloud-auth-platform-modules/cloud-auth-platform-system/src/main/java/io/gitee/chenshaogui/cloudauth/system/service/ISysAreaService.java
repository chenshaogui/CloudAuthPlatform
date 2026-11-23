package io.gitee.chenshaogui.cloudauth.system.service;


import io.gitee.chenshaogui.cloudauth.system.domain.SysArea;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysAreaBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysAreaVo;

import java.util.List;

/**
 * ISysAreaService
 *
 * @author sgchen
 * @date 2025/8/29 16:21
 */
public interface ISysAreaService {

    List<SysAreaVo> getAreaList(SysAreaBo sysAreaBo);

    SysAreaVo getSysAreaByCode(String areaCode);

}
