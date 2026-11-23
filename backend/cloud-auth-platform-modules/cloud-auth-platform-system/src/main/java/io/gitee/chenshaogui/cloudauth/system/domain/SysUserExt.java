package io.gitee.chenshaogui.cloudauth.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户扩展信息（来自 ln_pro sys_sec_user 独有字段）
 *
 * <p>数据迁移专用表，与 sys_user 1:1 关联（主键 user_id 对齐 sys_user.user_id），
 * 存储 sys_user 主表不承载的扩展字段：来源标识、身份/企业/资质、CA 证书、桌面配置等。</p>
 *
 * @author Lion Li
 */
@Data
@TableName("sys_user_ext")
public class SysUserExt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（关联 sys_user.user_id，主键）
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    // ==================== 身份 / 真实姓名 ====================

    /**
     * 身份证号（IDENTIFY）
     */
    private String identify;

    /**
     * 证件号（IDENTIFYCARD）
     */
    private String identifyCard;

    /**
     * 真实姓名（REALUSERNAME）
     */
    private String realUserName;

    // ==================== 企业 / 资质 ====================

    /**
     * 统一社会信用代码（USCI）
     */
    private String usci;

    /**
     * 企业代码（ENTERPRISECODE）
     */
    private String enterpriseCode;

    /**
     * 道路运输证代码（TRANSPORTLICENSECODE）
     */
    private String transportLicenseCode;

    /**
     * 企业名称（ENTERPRISENAME）
     */
    private String enterpriseName;

    /**
     * 企业身份（ENTERPRISEIDENTITY）
     */
    private String enterpriseIdentity;

    /**
     * 从业资格证（QC）
     */
    private String qc;

    /**
     * 所在市区（CITYDISTRICT）
     */
    private String cityDistrict;

    // ==================== 账号配置 ====================

    /**
     * 是否强制重置密码（Y=是，N=否）—— 由 ln_pro 迁移的用户首次登录后强制改密
     */
    private String pwdResetFlag;

    /**
     * 共享用户标识（SHAREDUSER）
     */
    private String sharedUser;

    /**
     * 老系统用户类型（USERTYPE 数字）
     */
    private Integer userTypeLn;

    /**
     * 用户级别（LEVEL_ID）
     */
    private Long levelId;

    /**
     * 是否发送日报（NEEDSENDDAILYPAPER）
     */
    private String needSendDailyPaper;

    /**
     * CA 证书 ID
     */
    private String caId;

    // ==================== 桌面 / 背景 ====================

    /**
     * 桌面配置 XML（DESKTOPCONFIG）
     */
    private String desktopConfig;

    /**
     * 背景图片（BACKGROUND）
     */
    private String background;

    /**
     * 用户来源类型（USER_SOURCE_TYPE）
     */
    private String userSourceType;

}