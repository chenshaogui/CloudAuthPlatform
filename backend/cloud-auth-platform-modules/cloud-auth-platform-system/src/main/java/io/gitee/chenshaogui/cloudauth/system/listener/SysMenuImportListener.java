package io.gitee.chenshaogui.cloudauth.system.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.http.HtmlUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.DateUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ValidatorUtils;
import io.gitee.chenshaogui.cloudauth.common.excel.core.ExcelListener;
import io.gitee.chenshaogui.cloudauth.common.excel.core.ExcelResult;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.api.model.LoginUser;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysMenuBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysMenuImportVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysMenuService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

/**
 * 系统菜单自定义导入
 *
 * @author Lion Li
 * @deprecated 已不再使用：{@code /system/menu/importData} 统一走
 * {@link io.gitee.chenshaogui.cloudauth.system.service.ISysMenuService#importMenuDataByParent}，
 * 本类流式导入无法重建「源菜单ID -> 实际菜单ID」映射，会导致父子层级断裂。
 */
@Slf4j
public class SysMenuImportListener extends AnalysisEventListener<SysMenuImportVo> implements ExcelListener<SysMenuImportVo> {

    private final ISysMenuService sysMenuService;

    private final Boolean isUpdateSupport;

    LoginUser loginUser;

    private int successNum = 0;
    private int failureNum = 0;
    private final StringBuilder successMsg = new StringBuilder();
    private final StringBuilder failureMsg = new StringBuilder();

    public SysMenuImportListener(Boolean isUpdateSupport) {
        this.sysMenuService = SpringUtils.getBean(ISysMenuService.class);
        this.isUpdateSupport = isUpdateSupport;
        this.loginUser = LoginHelper.getLoginUser();
    }

    @Override
    public void invoke(SysMenuImportVo menuImportVo, AnalysisContext context) {
        SysMenuBo sysMenuBo = BeanUtil.toBean(menuImportVo, SysMenuBo.class);
        ValidatorUtils.validate(sysMenuBo);
        boolean nameUnique = this.sysMenuService.checkMenuNameExists(sysMenuBo);
        Date nowDate = DateUtils.getNowDate();
        try {
            // 验证是否存在这个菜单
            if (!nameUnique) {
                sysMenuBo.setCreateDept(loginUser.getDeptId());
                sysMenuBo.setCreateBy(loginUser.getUserId());
                sysMenuBo.setCreateTime(nowDate);
                sysMenuBo.setUpdateBy(loginUser.getUserId());
                sysMenuBo.setUpdateTime(nowDate);
                sysMenuService.insertMenu(sysMenuBo);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、菜单 ").append(sysMenuBo.getMenuName()).append(" 导入成功");
            } else if (isUpdateSupport) {
                sysMenuBo.setUpdateBy(loginUser.getUserId());
                sysMenuBo.setUpdateTime(nowDate);
                sysMenuService.updateMenu(sysMenuBo);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、菜单 ").append(sysMenuBo.getMenuName()).append(" 更新成功");
            } else {
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、菜单 ").append(sysMenuBo.getMenuName()).append(" 已存在");
            }
        } catch (Exception e) {
            failureNum++;
            String msg = "<br/>" + failureNum + "、菜单 " + HtmlUtil.cleanHtmlTag(sysMenuBo.getMenuName()) + " 导入失败：";
            String message = e.getMessage();
            if (e instanceof ConstraintViolationException cvException) {
                message = StreamUtils.join(cvException.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
            }
            failureMsg.append(msg).append(message);
            log.error(msg, e);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {

    }

    @Override
    public ExcelResult<SysMenuImportVo> getExcelResult() {
        return new ExcelResult<>() {

            @Override
            public String getAnalysis() {
                if (failureNum > 0) {
                    failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
                    throw new ServiceException(failureMsg.toString());
                } else {
                    successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
                }
                return successMsg.toString();
            }

            @Override
            public List<SysMenuImportVo> getList() {
                return null;
            }

            @Override
            public List<String> getErrorList() {
                return null;
            }
        };
    }
}
