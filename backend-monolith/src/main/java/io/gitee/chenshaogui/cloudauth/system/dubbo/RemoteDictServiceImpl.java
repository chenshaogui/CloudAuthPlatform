package io.gitee.chenshaogui.cloudauth.system.dubbo;

import lombok.RequiredArgsConstructor;
import io.gitee.chenshaogui.cloudauth.common.core.utils.MapstructUtils;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteDictService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteDictDataVo;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteDictTypeVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDictDataVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysDictTypeVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDictDataService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysDictTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service

public class RemoteDictServiceImpl implements RemoteDictService {

    private final ISysDictTypeService sysDictTypeService;

    private final ISysDictDataService sysDictDataService;

    /**
     * 查询全部字典类型
     *
     * @return 字典类型集合信息
     */
    @Override
    public List<RemoteDictTypeVo> selectDictTypeAll() {
        List<SysDictTypeVo> list = sysDictTypeService.selectDictTypeAll();
        return MapstructUtils.convert(list, RemoteDictTypeVo.class);
    }

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public RemoteDictTypeVo selectDictTypeById(Long dictId) {
        SysDictTypeVo vo = sysDictTypeService.selectDictTypeById(dictId);
        return MapstructUtils.convert(vo, RemoteDictTypeVo.class);
    }

    @Override
    public RemoteDictTypeVo selectDictTypeByType(String dictType) {
        SysDictTypeVo vo = sysDictTypeService.selectDictTypeByType(dictType);
        return MapstructUtils.convert(vo, RemoteDictTypeVo.class);
    }

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public RemoteDictDataVo selectDictDataById(Long dictCode) {
        SysDictDataVo vo = sysDictDataService.selectDictDataById(dictCode);
        return MapstructUtils.convert(vo, RemoteDictDataVo.class);
    }

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<RemoteDictDataVo> selectDictDataByType(String dictType) {
        List<SysDictDataVo> list = sysDictTypeService.selectDictDataByType(dictType);
        return MapstructUtils.convert(list, RemoteDictDataVo.class);
    }

}
