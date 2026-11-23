package io.gitee.chenshaogui.cloudauth.securitycloud.oauth.bean;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 路由显示信息
 *
 * @author ruoyi
 */

@Data
@NoArgsConstructor
public class CloudMetaVo implements Serializable {

    /**
     * 设置该路由在侧边栏和面包屑中展示的名字
     */
    private String title;

    /**
     * 设置该路由的图标，对应路径src/assets/icons/svg
     */
    private String icon;

    /**
     * 设置为true，则不会被 <keep-alive>缓存
     */
    private Boolean noCache;

    /**
     * 内链地址（http(s)://开头）
     */
    private String link;

    /**
     * 激活菜单
     */
    private String activeMenu;

}
