package io.gitee.chenshaogui.cloudauth.casm.service;

import io.gitee.chenshaogui.cloudauth.casm.domain.bo.KeyInfoReqBo;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyAlthmEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.enums.KeyTypeEnum;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoRspVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.KeyInfoVo;
import io.gitee.chenshaogui.cloudauth.casm.domain.vo.SysKeyInfoVo;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.PageQuery;
import io.gitee.chenshaogui.cloudauth.common.mybatis.core.page.TableDataInfo;
import io.gitee.chenshaogui.cloudauth.casm.domain.bo.SysKeyInfoBo;

import java.util.Collection;
import java.util.List;

/**
 * 密钥信息管理Service接口
 *
 * @author Jll
 */
public interface ISysKeyInfoService {

    /**
     * 查询密钥信息管理
     */
    SysKeyInfoVo queryById(Long id);

    /**
     * 查询密钥信息信息基于密钥信息id
     */
    SysKeyInfoVo queryByKeyId(String keyId);

    /**
     * 新增密钥信息管理
     */
    Boolean insertByBo(SysKeyInfoBo bo);

    /**
     * 查询密钥信息管理
     */
    KeyInfoRspVo applyKey(KeyInfoReqBo reqBo);


    /**
     * 密钥使用
     */
    KeyInfoRspVo useKey(KeyInfoReqBo reqBo);


    /**
     * 密钥轮换
     */
    KeyInfoRspVo rotationKey(KeyInfoReqBo reqBo) throws Exception;

    /**
     * 密钥销毁
     */
    Boolean distroyKey(KeyInfoReqBo reqBo) throws Exception;
}
