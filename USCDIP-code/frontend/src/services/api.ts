import type {
  ApiRequestOptions,
  ApiResponse,
  AuthRefreshState,
  TokenPairResponse,
  TokenRefreshRequest
} from '@/types/api';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const ACCESS_TOKEN_KEY = 'uscdip.accessToken';
const REFRESH_TOKEN_KEY = 'uscdip.refreshToken';
const ACCESS_TOKEN_EXPIRES_AT_KEY = 'uscdip.accessTokenExpiresAt';
const REFRESH_TOKEN_EXPIRES_AT_KEY = 'uscdip.refreshTokenExpiresAt';
const TOKEN_TYPE_KEY = 'uscdip.tokenType';
const REFRESH_SKEW_MS = 2 * 60 * 1000;
const AUTH_SESSION_EVENT = 'uscdip:auth-session';

type AuthSessionEventType = 'refreshing' | 'refreshed' | 'force-logout';

export interface AuthSessionEventDetail {
  type: AuthSessionEventType;
  refreshState: AuthRefreshState;
  message: string;
  code?: string;
  traceId?: string;
}

let refreshPromise: Promise<TokenPairResponse> | null = null;

localStorage.removeItem(ACCESS_TOKEN_KEY);

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly traceId?: string;
  readonly payload?: ApiResponse<unknown>;

  constructor(message: string, status: number, payload?: ApiResponse<unknown>) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = payload?.error?.code;
    this.traceId = payload?.traceId;
    this.payload = payload;
  }
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getAccessTokenExpiresAt(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
}

export function getRefreshTokenExpiresAt(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_EXPIRES_AT_KEY);
}

export function getTokenType(): string | null {
  return sessionStorage.getItem(TOKEN_TYPE_KEY);
}

export function saveTokenPair(tokenPair: TokenPairResponse): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokenPair.accessToken);
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokenPair.refreshToken);
  sessionStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_KEY, tokenPair.accessTokenExpiresAt);
  sessionStorage.setItem(REFRESH_TOKEN_EXPIRES_AT_KEY, tokenPair.refreshTokenExpiresAt);
  sessionStorage.setItem(TOKEN_TYPE_KEY, tokenPair.tokenType);
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function clearTokenPair(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_EXPIRES_AT_KEY);
  sessionStorage.removeItem(TOKEN_TYPE_KEY);
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function clearAccessToken(): void {
  clearTokenPair();
}

export function addAuthSessionListener(
  listener: (detail: AuthSessionEventDetail) => void
): () => void {
  const handler = (event: Event) => {
    listener((event as CustomEvent<AuthSessionEventDetail>).detail);
  };
  window.addEventListener(AUTH_SESSION_EVENT, handler);
  return () => window.removeEventListener(AUTH_SESSION_EVENT, handler);
}

export function isAccessTokenExpiringSoon(): boolean {
  return isExpired(getAccessTokenExpiresAt(), REFRESH_SKEW_MS);
}

export function getAccessTokenRemainingMs(): number | null {
  return remainingMs(getAccessTokenExpiresAt());
}

function emitAuthSessionEvent(detail: AuthSessionEventDetail): void {
  window.dispatchEvent(new CustomEvent(AUTH_SESSION_EVENT, { detail }));
}

function isExpired(value: string | null, skewMs = 0): boolean {
  if (!value) {
    return true;
  }
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return true;
  }
  return timestamp - skewMs <= Date.now();
}

function remainingMs(value: string | null): number | null {
  if (!value) {
    return null;
  }
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return null;
  }
  return Math.max(timestamp - Date.now(), 0);
}

function shouldForceLogout(error: ApiClientError): boolean {
  return [
    'TOKEN_REFRESH_INVALID',
    'TOKEN_REFRESH_EXPIRED',
    'TOKEN_REFRESH_REPLAY_DETECTED',
    'TOKEN_REFRESH_REVOKED'
  ].includes(error.code ?? '');
}

function buildLocalAuthError(message: string, code: string): ApiClientError {
  return new ApiClientError(message, 401, {
    success: false,
    data: null,
    error: {
      code,
      message
    },
    traceId: '',
    timestamp: new Date().toISOString()
  });
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!payload) {
    throw new ApiClientError('后端返回了无法解析的响应', response.status);
  }
  if (!response.ok || !payload.success) {
    throw new ApiClientError(
      payload.error?.message ?? `HTTP ${response.status}`,
      response.status,
      payload as ApiResponse<unknown>
    );
  }
  return payload;
}

async function executeRequest<T>(
  path: string,
  options: RequestInit
): Promise<ApiResponse<T>> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  return parseApiResponse(response);
}

async function refreshTokenPair(): Promise<TokenPairResponse> {
  const plainRefreshToken = getRefreshToken();
  if (!plainRefreshToken) {
    throw buildLocalAuthError('缺少 refresh token，请重新登录。', 'TOKEN_REFRESH_INVALID');
  }
  if (isExpired(getRefreshTokenExpiresAt())) {
    throw buildLocalAuthError('Refresh token 已过期，请重新登录。', 'TOKEN_REFRESH_EXPIRED');
  }

  const body: TokenRefreshRequest = {
    refreshToken: plainRefreshToken
  };
  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });
  const payload = await parseApiResponse<TokenPairResponse>(response);
  if (!payload.data) {
    throw buildLocalAuthError('后端未返回新的 token pair。', 'TOKEN_REFRESH_INVALID');
  }
  saveTokenPair(payload.data);
  emitAuthSessionEvent({
    type: 'refreshed',
    refreshState: 'refreshed',
    message: '会话已刷新',
    traceId: payload.traceId
  });
  return payload.data;
}

async function refreshTokenWithLock(force = false): Promise<TokenPairResponse> {
  if (!force && !getAccessToken() && !getRefreshToken()) {
    throw buildLocalAuthError('未检测到登录令牌。', 'TOKEN_REFRESH_INVALID');
  }
  if (!force && getAccessToken() && !isAccessTokenExpiringSoon()) {
    const current = getAccessToken();
    const currentRefresh = getRefreshToken();
    if (current && currentRefresh) {
      return {
        accessToken: current,
        accessTokenExpiresAt: getAccessTokenExpiresAt() ?? '',
        refreshToken: currentRefresh,
        refreshTokenExpiresAt: getRefreshTokenExpiresAt() ?? '',
        tokenType: getTokenType() ?? 'Bearer',
        userSnapshot: {}
      };
    }
  }

  if (!refreshPromise) {
    emitAuthSessionEvent({
      type: 'refreshing',
      refreshState: 'refreshing',
      message: '正在静默刷新会话'
    });
    refreshPromise = refreshTokenPair()
      .catch((error) => {
        if (error instanceof ApiClientError) {
          clearTokenPair();
          emitAuthSessionEvent({
            type: 'force-logout',
            refreshState: 'failed',
            message: error.message,
            code: error.code,
            traceId: error.traceId
          });
        }
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

export async function refreshAccessToken(): Promise<TokenPairResponse> {
  return refreshTokenWithLock(true);
}

export async function refreshToken(): Promise<TokenPairResponse> {
  return refreshTokenWithLock(true);
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<ApiResponse<T>> {
  const {
    skipAuthRefresh = false,
    retryOnUnauthorized = true,
    ...requestOptions
  } = options;

  if (!skipAuthRefresh && getRefreshToken() && (!getAccessToken() || isAccessTokenExpiringSoon())) {
    await refreshTokenWithLock(false);
  }

  try {
    return await executeRequest<T>(path, requestOptions);
  } catch (error) {
    if (
      error instanceof ApiClientError &&
      error.status === 401 &&
      retryOnUnauthorized &&
      !skipAuthRefresh
    ) {
      if (!getRefreshToken()) {
        clearTokenPair();
        const localError = buildLocalAuthError('Refresh token 缺失，请重新登录。', 'TOKEN_REFRESH_INVALID');
        emitAuthSessionEvent({
          type: 'force-logout',
          refreshState: 'failed',
          message: localError.message,
          code: localError.code,
          traceId: error.traceId
        });
        throw localError;
      }
      try {
        await refreshTokenWithLock(true);
        return await executeRequest<T>(path, requestOptions);
      } catch (refreshError) {
        if (refreshError instanceof ApiClientError && shouldForceLogout(refreshError)) {
          throw refreshError;
        }
        throw refreshError;
      }
    }
    throw error;
  }
}
