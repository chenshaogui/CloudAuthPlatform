export interface OperLogQuery extends PageQuery {
  clientId: string;
  operIp: string;
  title: string;
  operName: string;
  businessType: string;
  status: string;
  orderByColumn: string;
  isAsc: string;
}

export interface OperLogVO extends BaseEntity {
  operId: string | number;
  tenantId: string;
  clientId: string;
  title: string;
  businessType: number;
  businessTypes: number[] | undefined;
  method: string;
  requestMethod: string;
  operatorType: number;
  operName: string;
  deptName: string;
  operUrl: string;
  operIp: string;
  operLocation: string;
  operParam: string;
  jsonResult: string;
  status: number;
  errorMsg: string;
  operTime: string;
  costTime: number;
}

export interface OperLogForm {
  operId: number | string | undefined;
  tenantId: string | number | undefined;
  clientId: string | undefined;
  title: string;
  businessType: number;
  businessTypes: number[] | undefined;
  method: string;
  requestMethod: string;
  operatorType: number;
  operName: string;
  deptName: string;
  operUrl: string;
  operIp: string;
  operLocation: string;
  operParam: string;
  jsonResult: string;
  status: number;
  errorMsg: string;
  operTime: string;
  costTime: number;
}
