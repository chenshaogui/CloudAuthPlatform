package io.gitee.chenshaogui.cloudauth.system.api;

import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteDictDataVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteDictTypeVo;

import java.util.List;

/**
 * 字典服务
 *
 * @author Lion Li
 */
public interface RemoteDictService {

    /**
     * 查询全部字典类型
     *
     * @return 字典类型集合信息
     */
    List<RemoteDictTypeVo> selectDictTypeAll();

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    RemoteDictTypeVo selectDictTypeById(Long dictId);

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    RemoteDictTypeVo selectDictTypeByType(String dictType);

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    RemoteDictDataVo selectDictDataById(Long dictCode);

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    List<RemoteDictDataVo> selectDictDataByType(String dictType);

}
