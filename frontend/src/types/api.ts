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
