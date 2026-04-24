export interface ApiError {
  code: string;
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  traceId: string;
  timestamp: string;
}

export interface ApiRequestOptions extends RequestInit {
  skipAuthRefresh?: boolean;
  retryOnUnauthorized?: boolean;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  hasNext: boolean;
}

export interface PlatformBoundary {
  platformCode: string;
  platformName: string;
  routePrefix: string;
  permissionPrefix: string;
  allowWrite: boolean;
  description: string;
}

export interface BoundarySpec {
  version: string;
  source: string;
  platforms: PlatformBoundary[];
  menuRules: Array<{
    ruleCode: string;
    name: string;
    expression: string;
  }>;
}

export interface AuthLoginDescriptor {
  enabled: boolean;
  registrationId: string;
  authorizationUrl: string | null;
  state: string | null;
  stateExpiresAt: string | null;
}

export interface AuthCallbackRequest {
  code: string;
  state: string;
  redirectUri: string;
}

export interface TokenPairResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  tokenType: string;
  userSnapshot: Record<string, unknown>;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

export type AuthRefreshState = 'idle' | 'refreshing' | 'refreshed' | 'failed';

export interface PendingOidcState {
  state: string;
  redirectUri: string;
  stateExpiresAt: string | null;
}

export interface AuthMePayload {
  userId: string;
  authenticationType: string;
  authMode?: string;
  snapshot: Record<string, unknown>;
}

export type PlatformCode = 'PORTAL' | 'MGMT' | 'EMGC' | 'DIAG' | 'SUPPORT';
export type ManagedPlatformCode = 'MGMT' | 'EMGC' | 'DIAG';

export interface UserPermissionSnapshot {
  userId: string;
  username: string;
  displayName: string;
  roleCodes: string[];
  permissionCodes: string[];
  dataScopeRule: string;
  authorizedRegions: string[];
  authorizedAssignees: string[];
  dataViewConstraint: string;
  topicPatterns: string[];
  maskedFeatureOnly: boolean;
}

export interface RoutePermissionMeta {
  requiresAuth: boolean;
  platformCode: ManagedPlatformCode;
  entryPermission: string;
  menuPermission: string;
  permissionLabel: string;
}

export interface PermissionCheckResult {
  allowed: boolean;
  requiredPermissions: string[];
  missingPermissions: string[];
  reason: string;
}

export interface ButtonPermissionConfig {
  key: string;
  label: string;
  description: string;
  requiredPermissions: string[];
  tone: 'read' | 'write' | 'dispatch' | 'model';
}

export interface WorkOrderResponse {
  workOrderId: string;
  incidentId: string;
  segmentId: string | null;
  nodeId: string | null;
  workOrderType: string;
  priority: string;
  description: string;
  assigneeUserId: string | null;
  assignee: string | null;
  status: string;
  slaDueAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationResponse {
  notificationId: string;
  sourceEventId: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  recipientUserId: string;
  recipientUsername: string;
  title: string;
  content: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  lastError: string | null;
}

export interface GisPointResponse {
  x: number;
  y: number;
}

export interface GisBboxResponse {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export type GisObjectType = 'NODE' | 'SEGMENT' | 'FACILITY' | 'DEVICE';
export type MapLayerKey = GisObjectType | 'ALERT';

export interface GisObjectRecordResponse {
  objectType: GisObjectType | string;
  objectId: string;
  objectName: string;
  regionId: string;
  authoritySrid: string;
  displaySrid: string;
  geometry2d: string;
  anchorPoint: GisPointResponse;
  bbox: GisBboxResponse;
  relatedObjectIds: Record<string, string[]>;
  attributes: Record<string, unknown>;
}

export interface GisObjectPickRequest {
  x: number;
  y: number;
  authoritySrid: string;
  displaySrid: string;
  objectTypes: string[];
  toleranceMeters: number;
}

export interface GisObjectPickResponse {
  object: GisObjectRecordResponse;
  distanceMeters: number;
  toleranceMeters: number;
}

export interface GisBboxQuery {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  authoritySrid: string;
  displaySrid: string;
  objectType?: string;
  page: number;
  pageSize: number;
}

export interface MapLayerLegendItem {
  label: string;
  color: string;
  shape: 'line' | 'circle' | 'diamond' | 'ring';
  description: string;
}

export interface MapLayerState {
  visible: boolean;
  opacity: number;
  legendCollapsed: boolean;
}

export interface MapLayerConfig {
  key: MapLayerKey;
  label: string;
  shortLabel: string;
  description: string;
  color: string;
  legendItems: MapLayerLegendItem[];
}

export interface LayerPersistencePayload {
  version: 1;
  layers: Partial<Record<MapLayerKey, MapLayerState>>;
}

export interface AlertRecordResponse {
  alertId: string;
  sourceRecordId: string;
  sourceBatchId: string;
  deviceId: string | null;
  segmentId: string | null;
  nodeId: string | null;
  ruleCode: string;
  severity: string;
  decision: string;
  alertConfRaw: number | null;
  alertConfFinal: number | null;
  dqScoreSnapshot: number | null;
  dqLevelSnapshot: string | null;
  dqAlarmConfFactor: number | null;
  metricCode: string | null;
  metricValue: string | null;
  eventTime: string | null;
  traceId: string | null;
  caseId: string | null;
  dedupeKey: string | null;
  processStatus: string | null;
  suppressed: boolean | null;
  escalationLevel: number | null;
  processedAt: string | null;
  createdAt: string | null;
}
