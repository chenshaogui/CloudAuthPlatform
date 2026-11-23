package io.gitee.chenshaogui.cloudauth.system.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.HtmlUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserClientConfigBo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserClientConfigService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.utils.SpringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StreamUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.ValidatorUtils;
import io.gitee.chenshaogui.cloudauth.common.excel.core.ExcelListener;
import io.gitee.chenshaogui.cloudauth.common.excel.core.ExcelResult;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserImportVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysConfigService;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户自定义导入
 *
 * @author Lion Li
 */
@Slf4j
public class SysUserImportListener extends AnalysisEventListener<SysUserImportVo> implements ExcelListener<SysUserImportVo> {

    private final ISysUserService userService;

    private final ISysUserClientConfigService userClientConfigService;

    private final String password;

    private final String clientId;

    private final Boolean isUpdateSupport;

    private final Long operUserId;

    private int successNum = 0;
    private int failureNum = 0;
    private final StringBuilder successMsg = new StringBuilder();
    private final StringBuilder failureMsg = new StringBuilder();

    public SysUserImportListener(Boolean isUpdateSupport, String clientId) {
        String initPassword = SpringUtils.getBean(ISysConfigService.class).selectConfigByKey("sys.user.initPassword");
        this.userService = SpringUtils.getBean(ISysUserService.class);
        this.userClientConfigService = SpringUtils.getBean(ISysUserClientConfigService.class);
        this.password = BCrypt.hashpw(initPassword);
        this.isUpdateSupport = isUpdateSupport;
        this.operUserId = LoginHelper.getUserId();
        this.clientId = clientId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void invoke(SysUserImportVo userVo, AnalysisContext context) {
        SysUserVo sysUser = this.userService.selectUserByUserName(userVo.getUserName());
        try {
            // 验证是否存在这个用户
            if (ObjectUtil.isNull(sysUser)) {
                SysUserBo user = BeanUtil.toBean(userVo, SysUserBo.class);
                ValidatorUtils.validate(user);
                user.setPassword(password);
                user.setCreateBy(operUserId);
                userService.insertUser(user);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、账号 ").append(user.getUserName()).append(" 导入成功");
                // 绑定用户与客户端关联关系
                if(StringUtils.isNotEmpty(clientId)){
                    SysUserClientConfigBo clientConfigBo = BeanUtil.toBean(user, SysUserClientConfigBo.class);
                    // userVo已存在userId，desensitizeFlag，和belongCode、belongName
                    clientConfigBo.setClientId(clientId);
                    clientConfigBo.setUserId(user.getUserId());
                    userClientConfigService.saveOrUpdateConfig(clientConfigBo);
                }
            } else if (isUpdateSupport) {
                Long userId = sysUser.getUserId();
                SysUserBo user = BeanUtil.toBean(userVo, SysUserBo.class);
                user.setUserId(userId);
                ValidatorUtils.validate(user);
                userService.checkUserAllowed(user.getUserId());
                userService.checkUserDataScope(user.getUserId());
                user.setUpdateBy(operUserId);
                userService.updateUser(user);
                // 绑定用户与客户端关联关系
                if(StringUtils.isNotEmpty(clientId)){
                    SysUserClientConfigBo clientConfigBo = BeanUtil.toBean(userVo, SysUserClientConfigBo.class);
                    // userVo已存在userId，desensitizeFlag，和belongCode、belongName
                    clientConfigBo.setClientId(clientId);
                    clientConfigBo.setUserId(userId);
                    userClientConfigService.saveOrUpdateConfig(clientConfigBo);
                }
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、账号 ").append(user.getUserName()).append(" 更新成功");
            } else {
                // 绑定用户与客户端关联关系
                Long userId = sysUser.getUserId();
                if(StringUtils.isNotEmpty(clientId)){
                    SysUserClientConfigBo clientConfigBo = BeanUtil.toBean(userVo, SysUserClientConfigBo.class);
                    // userVo已存在userId，desensitizeFlag，和belongCode、belongName
                    clientConfigBo.setClientId(clientId);
                    clientConfigBo.setUserId(userId);
                    userClientConfigService.saveOrUpdateConfig(clientConfigBo);
                }
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、账号 ").append(sysUser.getUserName()).append(" 已存在");
            }
        } catch (Exception e) {
            failureNum++;
            String msg = "<br/>" + failureNum + "、账号 " + HtmlUtil.cleanHtmlTag(userVo.getUserName()) + " 导入失败：";
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
    public ExcelResult<SysUserImportVo> getExcelResult() {
        return new ExcelResult<SysUserImportVo>() {

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
            public List<SysUserImportVo> getList() {
                return null;
            }

            @Override
            public List<String> getErrorList() {
                return null;
            }
        };
    }
}
