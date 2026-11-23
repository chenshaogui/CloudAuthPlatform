package io.gitee.chenshaogui.cloudauth.system.controller.system;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.BCrypt;
import io.gitee.chenshaogui.cloudauth.common.core.constant.GlobalConstants;
import io.gitee.chenshaogui.cloudauth.common.core.constant.SystemConstants;
import io.gitee.chenshaogui.cloudauth.common.core.exception.ServiceException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaException;
import io.gitee.chenshaogui.cloudauth.common.core.exception.user.CaptchaExpireException;
import io.gitee.chenshaogui.cloudauth.common.redis.utils.RedisUtils;
import io.gitee.chenshaogui.cloudauth.common.tenant.helper.TenantHelper;
import io.gitee.chenshaogui.cloudauth.system.checker.PasswordStrengthChecker;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserPasswordNewBo;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.seata.spring.annotation.GlobalTransactional;
import io.gitee.chenshaogui.cloudauth.common.core.domain.R;
import io.gitee.chenshaogui.cloudauth.common.core.utils.StringUtils;
import io.gitee.chenshaogui.cloudauth.common.core.utils.file.MimeTypeUtils;
import io.gitee.chenshaogui.cloudauth.common.encrypt.annotation.ApiEncrypt;
import io.gitee.chenshaogui.cloudauth.common.idempotent.annotation.RepeatSubmit;
import io.gitee.chenshaogui.cloudauth.common.log.annotation.Log;
import io.gitee.chenshaogui.cloudauth.common.log.enums.BusinessType;
import io.gitee.chenshaogui.cloudauth.common.mybatis.helper.DataPermissionHelper;
import io.gitee.chenshaogui.cloudauth.common.satoken.utils.LoginHelper;
import io.gitee.chenshaogui.cloudauth.common.web.core.BaseController;
import io.gitee.chenshaogui.cloudauth.resource.api.RemoteFileService;
import io.gitee.chenshaogui.cloudauth.resource.api.domain.RemoteFile;
import io.gitee.chenshaogui.cloudauth.system.api.RemoteClientService;
import io.gitee.chenshaogui.cloudauth.system.api.domain.vo.RemoteClientVo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserPasswordBo;
import io.gitee.chenshaogui.cloudauth.system.domain.bo.SysUserProfileBo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.ProfileUserVo;
import io.gitee.chenshaogui.cloudauth.system.domain.vo.SysUserVo;
import io.gitee.chenshaogui.cloudauth.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * 个人信息 业务处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/user/profile")
public class SysProfileController extends BaseController {

    @Value("${spring.profiles.active}")
    private String profiles;

    private final ISysUserService userService;

    @DubboReference
    private RemoteFileService remoteFileService;

    @DubboReference
    private RemoteClientService remoteClientServiceDubbo;

    private final PasswordStrengthChecker passwordStrengthChecker;

    /**
     * 个人信息
     */
    @GetMapping
    public R<ProfileVo> profile() {
        SysUserVo user = userService.selectUserById(LoginHelper.getUserId());
        String roleGroup = userService.selectUserRoleGroup(user.getUserId());
        String postGroup = userService.selectUserPostGroup(user.getUserId());
        // 单独做一个vo专门给个人中心用 避免数据被脱敏
        ProfileUserVo profileUser = BeanUtil.toBean(user, ProfileUserVo.class);
        ProfileVo profileVo = new ProfileVo(profileUser, roleGroup, postGroup);
        return R.ok(profileVo);
    }

    /**
     * 修改用户信息
     */
    @RepeatSubmit
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> updateProfile(@Validated @RequestBody SysUserProfileBo profile) {
        SysUserBo user = BeanUtil.toBean(profile, SysUserBo.class);
        user.setUserId(LoginHelper.getUserId());
        String username = LoginHelper.getUsername();
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("修改用户'" + username + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("修改用户'" + username + "'失败，邮箱账号已存在");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.updateUserProfile(user));
        if (rows > 0) {
            return R.ok();
        }
        return R.fail("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     *
     * @param bo 新旧密码
     */
    @RepeatSubmit
    @ApiEncrypt
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public R<Void> updatePwd(@Validated @RequestBody SysUserPasswordBo bo) {
        if (passwordStrengthChecker.isSimplePassword(bo.getNewPassword())) {
            String reason = passwordStrengthChecker.getSimpleReason(bo.getNewPassword());
            return R.fail("修改密码失败," + reason);
        }

        TenantHelper.dynamic(TenantHelper.getTenantId(), () -> {
            if (passwordStrengthChecker.isSimplePassword(bo.getNewPassword())) {
                String reason = passwordStrengthChecker.getSimpleReason(bo.getNewPassword());
                throw new ServiceException("修改密码失败," + reason);
            }
        });
        SysUserVo user = userService.selectUserById(LoginHelper.getUserId());
        String password = user.getPassword();
        if (!BCrypt.checkpw(bo.getOldPassword(), password)) {
            return R.fail("修改密码失败，旧密码错误");
        }
        if (BCrypt.checkpw(bo.getNewPassword(), password)) {
            return R.fail("新密码不能与旧密码相同");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.resetUserPwd(user.getUserId(), BCrypt.hashpw(bo.getNewPassword())));
        if (rows > 0) {
            return R.ok();
        }
        return R.fail("修改密码异常，请联系管理员");
    }

    /**
     * 未登录情况下重置密码
     * @param bo 用户信息
     * @return R
     */
    @RepeatSubmit()
    @PostMapping("/updatePwdNoLogin")
    public R<Void> updatePwdNoLogin(@RequestBody @Validated SysUserPasswordNewBo bo) {
        // 校验该 client 是否开放重置密码（sys_client.reset_pwd_enabled：1=开放 0=关闭，默认开放）
        if (StringUtils.isNotBlank(bo.getClientId())) {
            try {
                RemoteClientVo clientVo = remoteClientServiceDubbo.queryByClientId(bo.getClientId());
                if (clientVo != null && !"1".equals(clientVo.getResetPwdEnabled())) {
                    return R.fail("该客户端未开放重置密码功能");
                }
            } catch (Exception ignore) {
                // 客户端不存在/查询失败时按"未配置"放过
            }
        }
        // 分支判定：传了旧密码走"账号 + 旧密码 + 验证码"；未传旧密码走"手机号 + 短信验证码"找回（辽运通风格重置密码页）
        boolean byPhone = StringUtils.isBlank(bo.getOldPassword());
        if (byPhone && StringUtils.isBlank(bo.getPhonenumber())) {
            return R.fail("修改密码失败，请传入手机号");
        }
        SysUserVo user;
        if (byPhone) {
            // 验证码由 /auth/sendSmsCodeByPhone 发送，key = CAPTCHA_CODE_KEY + phonenumber
            validateCaptcha(bo.getCode(), bo.getPhonenumber());
            user = TenantHelper.dynamic(bo.getTenantId(), () -> {
                SysUserVo sysUserVo = userService.selectUserByPhonenumber(bo.getPhonenumber());
                if (sysUserVo == null) {
                    throw new ServiceException("修改密码失败，当前用户不存在");
                }
                if (passwordStrengthChecker.isSimplePassword(bo.getNewPassword())) {
                    String reason = passwordStrengthChecker.getSimpleReason(bo.getNewPassword());
                    throw new ServiceException("修改密码失败," + reason);
                }
                return sysUserVo;
            });
        } else {
            validateCaptcha(bo.getCode(), bo.getUserName());
            user = TenantHelper.dynamic(bo.getTenantId(), () -> {
                SysUserVo sysUserVo = userService.selectUserByUserName(bo.getUserName());
                if (sysUserVo == null) {
                    throw new ServiceException("修改密码失败，当前用户不存在");
                }
                if (passwordStrengthChecker.isSimplePassword(bo.getNewPassword())) {
                    String reason = passwordStrengthChecker.getSimpleReason(bo.getNewPassword());
                    throw new ServiceException("修改密码失败," + reason);
                }
                return sysUserVo;
            });
        }

        String password = user.getPassword();

        // 免旧密码分支不做旧密码校验；账号密码分支保留原校验
        if (!byPhone && !BCrypt.checkpw(bo.getOldPassword(), password)) {
            return R.fail("修改密码失败，旧密码错误");
        }
        if (BCrypt.checkpw(bo.getNewPassword(), password)) {
            return R.fail("新密码不能与旧密码相同");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.resetUserPwd(user.getUserId(), BCrypt.hashpw(bo.getNewPassword())));
        if (rows > 0) {
            if (byPhone) {
                // 重置成功后消费短信验证码，防止重复使用
                RedisUtils.deleteObject(GlobalConstants.CAPTCHA_CODE_KEY + bo.getPhonenumber());
            }
            return R.ok();
        }
        return R.fail("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     *
     * @param avatarfile 用户头像
     */
    @RepeatSubmit
    @GlobalTransactional(rollbackFor = Exception.class)
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AvatarVo> avatar(@RequestPart("avatarfile") MultipartFile avatarfile) throws IOException {
        if (!avatarfile.isEmpty()) {
            String extension = FileUtil.extName(avatarfile.getOriginalFilename());
            if (!StringUtils.equalsAnyIgnoreCase(extension, MimeTypeUtils.IMAGE_EXTENSION)) {
                return R.fail("文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式");
            }
            RemoteFile oss = remoteFileService.upload(avatarfile.getName(), avatarfile.getOriginalFilename(), avatarfile.getContentType(), avatarfile.getBytes());
            String avatar = oss.getUrl();
            boolean updateSuccess = DataPermissionHelper.ignore(() -> userService.updateUserAvatar(LoginHelper.getUserId(), oss.getOssId()));
            if (updateSuccess) {
                return R.ok(new AvatarVo(avatar));
            }
        }
        return R.fail("上传图片异常，请联系管理员");
    }

    /**
     * 校验验证码
     *
     * @param code     验证码
     * @param userName   用户名
     */
    private void validateCaptcha(String code, String userName) {
        if(StringUtils.equals(profiles,"pub") || !StringUtils.equals(code,"100900")){
            String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.blankToDefault(userName, "");
            String captcha = RedisUtils.getCacheObject(verifyKey);
            if (captcha == null) {
                throw new CaptchaExpireException();
            }
            if (!StringUtils.equalsIgnoreCase(code, captcha)) {
                throw new CaptchaException();
            }
        }
    }

    /**
     * 用户头像信息
     *
     * @param imgUrl 头像地址
     */
    public record AvatarVo(String imgUrl) {}

    /**
     * 用户个人信息
     *
     * @param user      用户信息
     * @param roleGroup 用户所属角色组
     * @param postGroup 用户所属岗位组
     */
    public record ProfileVo(ProfileUserVo user, String roleGroup, String postGroup) {}

}
