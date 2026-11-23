/**
 * 部门查询参数
 */
export interface DeptQuery extends PageQuery {
  deptName?: string;
  deptCode?: string;
  deptCategory?: string;
  status?: number;
}

/**
 * 部门扩展信息（来自 sys_dept_ext，ln_pro 独有字段）
 * <p>字段来自 02_ddl_扩展表.sql 的 sys_dept_ext；保存为表单可选项，不提交时不更新。</p>
 */
export interface DeptExtVO {
  deptId?: number | string;
  // 联系方式
  deptManager?: string;
  deptManagerTel?: string;
  fax?: string;
  postcode?: string;
  address?: string;
  onDutyTel?: string;
  url?: string;
  // 名称 / 描述
  shortName?: string;
  printName?: string;
  description?: string;
  // 区域 / 类型
  deptType?: string;
  deployed?: number;
  happenArea?: number;
  area?: string;
  used?: number;
}

/**
 * 部门类型
 */
export interface DeptVO extends BaseEntity {
  id: number | string;
  parentName: string;
  parentId: number | string;
  children: DeptVO[];
  deptId: number | string;
  deptName: string;
  deptCode?: string;
  deptCategory: string;
  orderNum: number;
  leader: string;
  phone: string;
  email: string;
  status: string;
  delFlag: string;
  ancestors: string;
  menuId: string | number;
  /** 部门扩展信息（sys_dept_ext，ln_pro 独有字段；按需回填） */
  ext?: DeptExtVO;
}

/**
 * 部门类型
 */
export interface DeptTreeVO extends BaseEntity {
  id: number | string;
  label: string;
  parentId: number | string;
  weight: number;
  children: DeptTreeVO[];
  disabled: boolean;
}

/**
 * 部门表单类型
 */
export interface DeptForm {
  parentName?: string;
  parentId?: number | string;
  children?: DeptForm[];
  deptId?: number | string;
  deptName?: string;
  deptCode?: string;
  deptCategory?: string;
  orderNum?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: string;
  delFlag?: string;
  ancestors?: string;
  /** 部门扩展信息（sys_dept_ext；表单不展开时为 undefined，由后端按"不动"语义处理） */
  ext?: DeptExtVO;
}
