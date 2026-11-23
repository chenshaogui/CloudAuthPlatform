import { MenuTypeEnum } from '@/enums/MenuTypeEnum';

/**
 * 菜单树形结构类型
 */
export interface MenuTreeOption {
  id: string | number;
  label: string;
  parentId: string | number;
  weight: number;
  children?: MenuTreeOption[];
}

export interface RoleMenuTree {
  menus: MenuTreeOption[];
  checkedKeys: string[];
}

/**
 * 菜单查询参数类型
 */
export interface MenuQuery {
  keywords?: string;
  menuName?: string;
  status?: string;
}

/**
 * 菜单扩展信息（来自 sys_menu_ext，ln_pro 独有字段）
 * <p>字段来自 02_ddl_扩展表.sql 的 sys_menu_ext；保存为表单可选项，不提交时不更新。</p>
 */
export interface MenuExtVO {
  menuId?: string | number;
  // 路由 / 跳转
  isRoute?: number;
  keepAlive?: number;
  redirectUrl?: string;
  internalExternal?: number;
  className?: string;
  // 日志 / 桌面
  rememberLog?: number;
  logString?: string;
  config?: string;
}

/**
 * 菜单视图对象类型
 */
export interface MenuVO extends BaseEntity {
  parentName: string;
  parentId: string | number;
  children: MenuVO[];
  menuId: string | number;
  menuName: string;
  orderNum: number;
  path: string;
  component: string;
  queryParam: string;
  isFrame: string;
  isCache: string;
  menuType: MenuTypeEnum;
  visible: string;
  status: string;
  icon: string;
  remark: string;
  appId?: string | number;
  /** 菜单扩展信息（sys_menu_ext，ln_pro 独有字段；按需回填） */
  ext?: MenuExtVO;
}

export interface MenuForm {
  parentName?: string;
  parentId?: string | number;
  children?: MenuForm[];
  menuId?: string | number;
  menuName: string;
  orderNum: number;
  path: string;
  component?: string;
  queryParam?: string;
  isFrame?: string;
  isCache?: string;
  menuType?: MenuTypeEnum;
  visible?: string;
  status?: string;
  icon?: string;
  remark?: string;
  query?: string;
  perms?: string;
  clientId?: string;
  appId?: string | number;
  /** 菜单扩展信息（sys_menu_ext；表单不展开时为 undefined，由后端按"不动"语义处理） */
  ext?: MenuExtVO;
}
