import type {
  AuthMePayload,
  ButtonPermissionConfig,
  ManagedPlatformCode,
  PermissionCheckResult,
  PlatformBoundary,
  UserPermissionSnapshot
} from '@/types/api';

export const PLATFORM_CODES: ManagedPlatformCode[] = ['MGMT', 'EMGC', 'DIAG'];

export const PLATFORM_PERMISSION_REQUIREMENTS: Record<
  ManagedPlatformCode,
  {
    entryPermission: string;
    menuPermission: string;
    writePermission: string;
    actionLabel: string;
  }
> = {
  MGMT: {
    entryPermission: 'ENTRY:MGMT',
    menuPermission: 'MENU:ASSET:READ',
    writePermission: 'MENU:ASSET:WRITE',
    actionLabel: '资产编辑'
  },
  EMGC: {
    entryPermission: 'ENTRY:EMGC',
    menuPermission: 'MENU:WORKORDER:READ',
    writePermission: 'MENU:WORKORDER:DISPATCH',
    actionLabel: '工单派发'
  },
  DIAG: {
    entryPermission: 'ENTRY:DIAG',
    menuPermission: 'MENU:MODEL:READ',
    writePermission: 'MENU:MODEL:WRITE',
    actionLabel: '模型发布'
  }
};

export const PLATFORM_ACTIONS: Record<ManagedPlatformCode, ButtonPermissionConfig[]> = {
  MGMT: [
    {
      key: 'asset-read',
      label: '查看资产台账',
      description: '读取节点、管段、设备等主数据摘要。',
      requiredPermissions: ['ENTRY:MGMT', 'MENU:ASSET:READ'],
      tone: 'read'
    },
    {
      key: 'asset-write',
      label: '提交资产变更',
      description: '后续进入综合管理平台内的资产编辑流程。',
      requiredPermissions: ['ENTRY:MGMT', 'MENU:ASSET:WRITE'],
      tone: 'write'
    }
  ],
  EMGC: [
    {
      key: 'workorder-read',
      label: '查看应急工单',
      description: '读取告警处置、通知和工单摘要。',
      requiredPermissions: ['ENTRY:EMGC', 'MENU:WORKORDER:READ'],
      tone: 'read'
    },
    {
      key: 'workorder-dispatch',
      label: '派发处置工单',
      description: '后续进入应急指挥平台内的派单流程。',
      requiredPermissions: ['ENTRY:EMGC', 'MENU:WORKORDER:DISPATCH'],
      tone: 'dispatch'
    }
  ],
  DIAG: [
    {
      key: 'model-read',
      label: '查看模型运行',
      description: '读取模型版本、推理结果与脱敏特征摘要。',
      requiredPermissions: ['ENTRY:DIAG', 'MENU:MODEL:READ'],
      tone: 'read'
    },
    {
      key: 'model-write',
      label: '发布模型版本',
      description: '后续进入智能诊断中枢内的模型发布流程。',
      requiredPermissions: ['ENTRY:DIAG', 'MENU:MODEL:WRITE'],
      tone: 'model'
    }
  ]
};

export function isManagedPlatformCode(value: string): value is ManagedPlatformCode {
  return PLATFORM_CODES.includes(value as ManagedPlatformCode);
}

export function extractPermissionSnapshot(user: AuthMePayload | null): UserPermissionSnapshot {
  const snapshot = user?.snapshot ?? {};

  return {
    userId: user?.userId ?? '',
    username: readString(snapshot.username) || readString(snapshot.userName) || user?.userId || '',
    displayName:
      readString(snapshot.displayName) ||
      readString(snapshot.name) ||
      readString(snapshot.username) ||
      user?.userId ||
      '',
    roleCodes: normalizeCodeList(snapshot.roleCodes),
    permissionCodes: normalizeCodeList(snapshot.permissionCodes),
    dataScopeRule: readString(snapshot.dataScopeRule),
    authorizedRegions: normalizeStringList(snapshot.authorizedRegions),
    authorizedAssignees: normalizeStringList(snapshot.authorizedAssignees),
    dataViewConstraint: readString(snapshot.dataViewConstraint),
    topicPatterns: normalizeStringList(snapshot.topicPatterns),
    maskedFeatureOnly: snapshot.maskedFeatureOnly === true
  };
}

export function hasPermission(user: AuthMePayload | null, permission: string): boolean {
  return extractPermissionSnapshot(user).permissionCodes.includes(permission.toUpperCase());
}

export function hasAllPermissions(
  user: AuthMePayload | null,
  permissions: string[]
): boolean {
  const granted = new Set(extractPermissionSnapshot(user).permissionCodes);
  return permissions.every((permission) => granted.has(permission.toUpperCase()));
}

export function checkPermissions(
  user: AuthMePayload | null,
  requiredPermissions: string[],
  subject: string
): PermissionCheckResult {
  const normalized = requiredPermissions.map((permission) => permission.toUpperCase());
  const granted = new Set(extractPermissionSnapshot(user).permissionCodes);
  const missingPermissions = normalized.filter((permission) => !granted.has(permission));

  return {
    allowed: missingPermissions.length === 0,
    requiredPermissions: normalized,
    missingPermissions,
    reason:
      missingPermissions.length === 0
        ? `${subject} 权限已满足`
        : `缺少 ${missingPermissions.join(' / ')}，无法使用${subject}`
  };
}

export function checkPlatformEntry(
  user: AuthMePayload | null,
  platformCode: string
): PermissionCheckResult {
  if (!isManagedPlatformCode(platformCode)) {
    return {
      allowed: false,
      requiredPermissions: [],
      missingPermissions: [],
      reason: '不是用户可见平台入口'
    };
  }
  const requirement = PLATFORM_PERMISSION_REQUIREMENTS[platformCode];
  return checkPermissions(user, [requirement.entryPermission], `${platformCode} 平台入口`);
}

export function checkPlatformRoute(
  user: AuthMePayload | null,
  platformCode: ManagedPlatformCode
): PermissionCheckResult {
  const requirement = PLATFORM_PERMISSION_REQUIREMENTS[platformCode];
  return checkPermissions(
    user,
    [requirement.entryPermission, requirement.menuPermission],
    `${platformCode} 平台`
  );
}

export function countAccessiblePlatforms(user: AuthMePayload | null): number {
  return PLATFORM_CODES.filter((platformCode) => checkPlatformRoute(user, platformCode).allowed)
    .length;
}

export function filterManagedPlatforms(platforms: PlatformBoundary[]): PlatformBoundary[] {
  return platforms.filter((platform) => isManagedPlatformCode(platform.platformCode));
}

function normalizeCodeList(value: unknown): string[] {
  return normalizeStringList(value).map((item) => item.toUpperCase());
}

function normalizeStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return typeof value === 'string' && value ? [value] : [];
  }
  return value.filter((item): item is string => typeof item === 'string' && item.length > 0);
}

function readString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}
