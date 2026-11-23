/**
 * 客户端个性化配置 TypeScript 类型
 *
 * <p>对应后端 {@code io.gitee.chenshaogui.cloudauth.system.domain.vo.ClientPublicConfigVo} 与
 * {@code sys_client.layout_config} JSON 字符串解析后的对象结构。</p>
 *
 * <p>所有字段均可选（后端 layout_config 可为空，未配置时模板走硬编码默认）。</p>
 */

/** 主题通用字段 */
export interface ClientTheme {
  /** 主色（用于按钮、链接、强调元素） */
  primaryColor?: string;
  /** 副色（用于辅助强调、hover 等） */
  secondaryColor?: string;
  /** Logo 图片 URL（不填则用默认） */
  logoUrl?: string;
  /** Logo 文字（如"辽运通"） */
  logoText?: string;
}

/** 登录页配置 */
export interface LoginLayoutConfig {
  /** 整页背景图 URL（OSS） */
  backgroundImage?: string;
  /** 左侧插画/装饰图 URL */
  leftImage?: string;
  /** 标题（卡片上方系统名） */
  title?: string;
  /** 表单区欢迎语（默认"欢迎登录！"） */
  welcomeText?: string;
  /** 副标题 */
  subtitle?: string;
  /** 表单位置：left / center / right，默认 right */
  formPosition?: 'left' | 'center' | 'right';
  /** 默认 Tab：account（账号密码） / sms（短信） */
  defaultTab?: 'account' | 'sms';
  /** 是否显示租户下拉 */
  showTenant?: boolean;
  /** 是否显示"记住密码" */
  showRememberMe?: boolean;
  /** 是否显示短信 Tab */
  showSmsTab?: boolean;
  /** 是否显示"忘记密码"入口 */
  showForgetPwd?: boolean;
  /** 是否显示"立即注册"入口 */
  showRegister?: boolean;
  /** 是否显示企业微信登录 */
  showWechat?: boolean;
  /** 协议 URL（点击"用户协议"打开） */
  agreementUrl?: string;
  /** 自定义底部链接 */
  bottomLinks?: Array<{ text: string; action: string; to?: string }>;
}

/** 注册页配置 */
export interface RegisterLayoutConfig {
  /** 整页背景图 URL */
  backgroundImage?: string;
  /** Logo URL */
  logoUrl?: string;
  /** 页面顶部大标题（平台名，如"辽运通智慧监管平台"） */
  platformTitle?: string;
  /** 表单区标题（默认"用户注册"） */
  title?: string;
  /** 角色单选列表（控制用户身份，如"法人/安全责任人"） */
  roles?: Array<{ label: string; value: string; default?: boolean }>;
  /** 是否需要邀请码 */
  showInviteCode?: boolean;
  /** 提交按钮文案 */
  submitText?: string;
  /** 协议 URL */
  agreementUrl?: string;
}

/** 重置密码页配置 */
export interface ResetPwdLayoutConfig {
  /** 整页背景图 URL */
  backgroundImage?: string;
  /** Logo URL */
  logoUrl?: string;
  /** 标题 */
  title?: string;
  /** 模式：steps（步骤条） / form（单表单） */
  mode?: 'steps' | 'form';
  /** 步骤文案（仅 mode=steps 时生效） */
  steps?: string[];
  /** 是否显示"返回登录"链接 */
  showBackToLogin?: boolean;
  /** 提交按钮文案 */
  submitText?: string;
}

/** layout_config JSON 解析后的结构 */
export interface ClientLayoutConfig {
  /** 配置版本号（便于未来兼容） */
  version?: number;
  /** 模板名，决定渲染哪套页面组件；未配置或不存在则走 default */
  template?: 'default' | 'liaoyuntong' | string;
  /** 全局主题 */
  theme?: ClientTheme;
  /** 登录页配置 */
  login?: LoginLayoutConfig;
  /** 注册页配置 */
  register?: RegisterLayoutConfig;
  /** 重置密码页配置 */
  resetPwd?: ResetPwdLayoutConfig;
}

/** 后端公共配置接口返回的完整 Vo */
export interface ClientPublicConfigVo {
  clientId: string;
  clientKey?: string;
  clientName?: string;
  status: string;
  deviceType?: string;
  /** 原始 layoutConfig JSON 字符串，store 中会同时保留原始 + 解析后的对象 */
  layoutConfig?: string;
  /** 解析后的 layoutConfig 对象（已 JSON.parse，未配置时为 null） */
  layoutConfigParsed?: ClientLayoutConfig;
  registerEnabled?: string;
  resetPwdEnabled?: string;
}
