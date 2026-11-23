/**
 * 密钥查询参数类型
 */
export interface KeyQuery extends PageQuery {
  clientId: string;
  keyName: string;
  keyType: string;
  keyAlthm: string;
}

export interface RotationKeyQuery extends PageQuery {
  id: string;
}

export interface KeyVo extends BaseEntity {
  id: string;
  keyId: string;
  tenantId: string;
  clientId: string;
  clientName: string;
  keyName: string;
  keyLevel: string;
  keyType: string;
  keyAlthm: string;
  expireTime: string;
  status: string;
  rotateCount: string;
  lastRotateTime: string;
}

export interface KeyForm {
  clientId: string;
  keyName: string;
  keyType: string;
  keyAlthm: string;
}

export interface keyInfoData {
  keyType: string;
  clientId: string;
}
