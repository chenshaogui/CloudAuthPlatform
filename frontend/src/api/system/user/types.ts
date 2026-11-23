import { RoleVO } from '@/api/system/role/types';
import { PostVO } from '@/api/system/post/types';

/**
 * 用户信息
 */
export interface UserInfo {
  user: UserVO;
  roles: string[];
  permissions: string[];
}

/**
 * 用户查询对象类型
 */
export interface UserQuery extends PageQuery {
  userName?: string;
  nickName?: string;
  phonenumber?: string;
  status?: string;
  deptId?: string | number;
  roleId?: string | number;
  userIds?: string | number | (string | number)[] | undefined;
  clientId?: string;
}

/**
 * 用户扩展信息（来自 sys_user_ext，ln_pro 独有字段）
 * <p>字段来自 02_ddl_扩展表.sql 的 sys_user_ext；保存为表单可选项，不提交时不更新。</p>
 */
export interface UserExtVO {
  userId?: string | number;
  // 身份 / 真实姓名
  identify?: string;
  identifyCard?: string;
  realUserName?: string;
  // 企业 / 资质
  usci?: string;
  enterpriseCode?: string;
  transportLicenseCode?: string;
  enterpriseName?: string;
  enterpriseIdentity?: string;
  qc?: string;
  cityDistrict?: string;
  // 账号配置
  pwdResetFlag?: string;
  sharedUser?: string;
  userTypeLn?: number;
  levelId?: string | number;
  needSendDailyPaper?: string;
  caId?: string;
  // 联系方式 / 桌面
  tel?: string;
  address?: string;
  desktopConfig?: string;
  background?: string;
  userSourceType?: string;
}

/**
 * 用户返回对象
 */
export interface UserVO extends BaseEntity {
  userId: string | number;
  tenantId: string;
  deptId: number;
  userName: string;
  nickName: string;
  userType: string;
  email: string;
  phonenumber: string;
  sex: string;
  avatar: string;
  status: string;
  delFlag: string;
  loginIp: string;
  loginDate: string;
  remark: string;
  deptName: string;
  roles: RoleVO[];
  roleIds: any;
  postIds: any;
  roleId: any;
  admin: boolean;
  /** 用户扩展信息（sys_user_ext，ln_pro 独有字段；按需回填） */
  ext?: UserExtVO;
}

/**
 * 用户表单类型
 */
export interface UserForm {
  id?: string;
  userId?: string;
  deptId?: number;
  userName: string;
  nickName?: string;
  password: string;
  phonenumber?: string;
  email?: string;
  sex?: string;
  status: string;
  remark?: string;
  postIds: string[];
  roleIds: string[];
  /** 用户扩展信息（sys_user_ext；表单不展开时为 undefined，由后端按"不动"语义处理） */
  ext?: UserExtVO;
}

export interface UserInfoVO {
  user: UserVO;
  roles: RoleVO[];
  roleIds: string[];
  posts: PostVO[];
  postIds: string[];
  roleGroup: string;
  postGroup: string;
}

export interface ResetPwdForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}
