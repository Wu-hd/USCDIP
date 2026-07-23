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
  postLoginRedirect?: string;
}

export interface AuthMePayload {
  userId: string;
  authenticationType: string;
  authMode?: string;
  snapshot: Record<string, unknown>;
}

export type AccountStatus = 'ACTIVE' | 'DISABLED';
export type AccountDataScopes = Record<string, string[]>;

export interface AccountResponse {
  userId: string;
  username: string;
  displayName: string;
  primaryRegionId: string;
  status: AccountStatus;
  roleCodes: string[];
  dataScopes: AccountDataScopes;
  localLoginEnabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AccountPageResponse {
  items: AccountResponse[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  hasNext: boolean;
  activeCount: number;
  disabledCount: number;
  platformAdminCount: number;
}

export interface AccountRoleOptionResponse {
  roleCode: string;
  roleName: string;
  description: string;
  readOnly: boolean;
}

export interface AccountOptionsResponse {
  roles: AccountRoleOptionResponse[];
  statuses: AccountStatus[];
  scopeTypes: string[];
}

export interface AccountCreateRequest {
  username: string;
  displayName: string;
  primaryRegionId: string;
  password: string;
  roleCodes: string[];
  dataScopes: AccountDataScopes;
}

export interface AccountUpdateRequest {
  displayName: string;
  primaryRegionId: string;
  status: AccountStatus;
  roleCodes: string[];
  dataScopes: AccountDataScopes;
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
  requiredRole?: string;
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
  createdBy?: string | null;
  dispatchedBy?: string | null;
  acceptedBy?: string | null;
  completedBy?: string | null;
  closedBy?: string | null;
  dispatchedAt?: string | null;
  acceptedAt?: string | null;
  completedAt?: string | null;
  closedAt?: string | null;
  completionSummary?: string | null;
  closeReason?: string | null;
  writebackType?: string | null;
  writebackReason?: string | null;
  writebackAt?: string | null;
  createdAt: string;
  updatedAt: string;
  versionNo?: number | null;
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

export type GisObjectType = 'NODE' | 'SEGMENT' | 'FACILITY' | 'DEVICE' | 'STATION';
export type MapLayerKey = GisObjectType | 'ALERT';
export type AssetSearchType = GisObjectType | 'ALL';
export type AssetDetailTab = 'profile' | 'devices' | 'alerts' | 'incidents' | 'workorders';

export interface AssetDetailTabState<T = unknown> {
  status: 'idle' | 'loading' | 'ready' | 'empty' | 'error';
  data: T;
  traceId: string;
  message: string;
}

export interface MasterDataRecordResponse {
  objectType: GisObjectType | string;
  objectId: string;
  objectName: string;
  status: string;
  regionId: string;
  relatedObjectIds: Record<string, string[]>;
  attributes: Record<string, unknown>;
}

export interface MasterChangeSubmitRequest {
  objectType: GisObjectType;
  objectId: string;
  baseVersionNo: number;
  reason: string;
  payload: Record<string, unknown>;
}

export interface MasterChangeResponse {
  requestId: string;
  objectType: GisObjectType | string;
  objectId: string;
  requestStatus: string;
  requestedBy: string;
  approvedBy: string | null;
  baseVersionNo: number;
  effectiveVersionNo: number | null;
  reason: string;
  payload: Record<string, unknown>;
  createdAt: string;
  approvedAt: string | null;
  updatedAt: string;
}

export interface AssetSearchResult {
  objectType: GisObjectType;
  objectId: string;
  objectName: string;
  status: string;
  regionId: string;
  relatedObjectIds: Record<string, string[]>;
  attributes: Record<string, unknown>;
  matchedBy: 'objectId' | 'objectName' | 'objectType';
}

export interface AssetSearchIndexState {
  status: 'idle' | 'loading' | 'ready' | 'error';
  loadedPages: number;
  total: number;
  reachedLimit: boolean;
  traceId: string;
  message: string;
}

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

export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection';
  features: Array<{
    type: 'Feature';
    properties?: Record<string, unknown> | null;
    geometry: {
      type: 'Point' | 'LineString' | 'MultiLineString';
      coordinates: unknown;
    };
  }>;
}

export interface StationPipelineLayoutResponse {
  stationId: string;
  layoutSrid: string;
  layout: GeoJsonFeatureCollection;
  updatedAt: string;
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

export interface DeviceLedgerResponse {
  deviceId: string;
  deviceName: string;
  facilityId: string | null;
  segmentId: string | null;
  nodeId: string | null;
  regionId: string | null;
  protocolType: string;
  onlineStatus: string;
  onlineStatusReason: string | null;
  lastHeartbeat: string | null;
  lastRecvTime: string | null;
  bufferLevel: number | null;
  abnormalFlags: string[];
  calibrationDueAt: string | null;
  calibrationExpired: boolean;
  versionNo: number | null;
}

export interface DataQualityScoreResponse {
  sourceRecordId: string;
  sourceBatchId: string;
  deviceId: string;
  metricCode: string;
  metricValue: string;
  eventTime: string | null;
  recvTime: string | null;
  deviceTime: string | null;
  isBackfill: boolean;
  dqScore: number | null;
  dqLevel: string | null;
  dqFlags: string | null;
  dqCompleteness: number | null;
  dqValidity: number | null;
  dqTimeliness: number | null;
  dqConsistency: number | null;
  dqStability: number | null;
  dqAlarmConfFactor: number | null;
  dqScoredAt: string | null;
}

export interface ModelVersionResponse {
  versionId: string;
  modelCode: string;
  versionNo: string;
  status: string;
  grayPercent: number | null;
  artifactUri: string | null;
  featureSchemaVersion: string | null;
  timeoutMs: number | null;
  ruleFallbackEnabled: boolean;
  publishedAt: string | null;
  rolledBackAt: string | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ModelResponse {
  modelCode: string;
  modelName: string;
  modelType: string;
  status: string;
  defaultTimeoutMs: number | null;
  ruleFallbackEnabled: boolean;
  description: string | null;
  activeVersionNo: string | null;
  grayVersionNo: string | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  versions: ModelVersionResponse[];
}

export interface ModelRollbackRequest {
  targetVersionNo: string;
  reason: string;
}

export interface TrendMetricPoint {
  sourceRecordId: string;
  eventTime: string;
  timestamp: number;
  metricValue: number;
  dqScore: number | null;
  dqLevel: string | null;
  dqFlags: string[];
  isBackfill: boolean;
  raw: DataQualityScoreResponse;
}

export interface IncidentResponse {
  incidentId: string;
  incidentType: string;
  segmentId: string | null;
  nodeId: string | null;
  deviceId: string | null;
  title: string;
  severity: string;
  severitySource: string | null;
  status: string;
  sourceCaseId: string | null;
  sourceAlertId: string | null;
  sourceRuleCode: string | null;
  sourceBatchId: string | null;
  dqScoreSnapshot: number | null;
  alertConfFinal: number | null;
  confirmedBy: string | null;
  confirmedAt: string | null;
  resolvedAt: string | null;
  closeReason: string | null;
  traceId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  versionNo: number | null;
}

export interface AssetDetailContext {
  objectKey: string;
  profile: AssetDetailTabState<MasterDataRecordResponse | null>;
  devices: AssetDetailTabState<DeviceLedgerResponse[]>;
  alerts: AssetDetailTabState<AlertRecordResponse[]>;
  incidents: AssetDetailTabState<IncidentResponse[]>;
  workorders: AssetDetailTabState<WorkOrderResponse[]>;
}

export interface WorkOrderCreateRequest {
  incidentId: string;
  workOrderType: string;
  priority: string;
  description: string;
  assigneeUserId?: string | null;
  assignee?: string | null;
  slaDueAt?: string | null;
}

export interface WorkOrderDispatchRequest {
  assigneeUserId?: string | null;
  assignee: string;
  slaDueAt?: string | null;
  reason?: string | null;
}

export interface WorkOrderCompleteRequest {
  completionSummary: string;
}

export interface WorkOrderCloseRequest {
  closeReason: string;
}

export interface WorkOrderWritebackRequest {
  writebackType: string;
  writebackReason: string;
}

export interface TimelineEventData {
  eventId: string;
  eventType: 'ALERT' | 'INCIDENT' | 'WORKORDER_CREATE' | 'WORKORDER_DISPATCH' | 'WORKORDER_ACCEPT' | 'WORKORDER_COMPLETE' | 'WORKORDER_CLOSE' | 'WORKORDER_WRITEBACK';
  title: string;
  description: string;
  timestamp: string;
  actor?: string;
  status?: string;
  metadata?: Record<string, unknown>;
}
