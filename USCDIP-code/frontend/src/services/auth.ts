import { apiRequest } from '@/services/api';
import type {
  AuthCallbackRequest,
  AuthLoginDescriptor,
  PendingOidcState,
  TokenPairResponse
} from '@/types/api';

const PENDING_OIDC_STATE_KEY = 'uscdip.oidc.pendingState';
const PROCESSED_OIDC_STATE_PREFIX = 'uscdip.oidc.processed.';
export const DEFAULT_POST_LOGIN_ROUTE = '/home';

export function exchangeOidcCallback(request: AuthCallbackRequest) {
  return apiRequest<TokenPairResponse>('/api/auth/callback', {
    method: 'POST',
    body: JSON.stringify(request),
    skipAuthRefresh: true
  });
}

export function savePendingOidcState(
  descriptor: AuthLoginDescriptor,
  redirectUri: string,
  postLoginTarget?: unknown
): void {
  if (!descriptor.state) {
    sessionStorage.removeItem(PENDING_OIDC_STATE_KEY);
    return;
  }

  const pendingState: PendingOidcState = {
    state: descriptor.state,
    redirectUri,
    stateExpiresAt: descriptor.stateExpiresAt,
    postLoginRedirect: resolvePostLoginRoute(postLoginTarget)
  };
  sessionStorage.setItem(PENDING_OIDC_STATE_KEY, JSON.stringify(pendingState));
}

export function getPendingOidcState(): PendingOidcState | null {
  const raw = sessionStorage.getItem(PENDING_OIDC_STATE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as PendingOidcState;
    return {
      ...parsed,
      postLoginRedirect: resolvePostLoginRoute(parsed.postLoginRedirect)
    };
  } catch {
    sessionStorage.removeItem(PENDING_OIDC_STATE_KEY);
    return null;
  }
}

export function clearPendingOidcState(): void {
  sessionStorage.removeItem(PENDING_OIDC_STATE_KEY);
}

export function isOidcStateProcessed(state: string): boolean {
  return sessionStorage.getItem(`${PROCESSED_OIDC_STATE_PREFIX}${state}`) === 'true';
}

export function markOidcStateProcessed(state: string): void {
  sessionStorage.setItem(`${PROCESSED_OIDC_STATE_PREFIX}${state}`, 'true');
}

export function resolvePostLoginRoute(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return DEFAULT_POST_LOGIN_ROUTE;
  }
  if (candidate.includes('\\')) {
    return DEFAULT_POST_LOGIN_ROUTE;
  }

  try {
    const parsed = new URL(candidate, window.location.origin);
    if (parsed.origin !== window.location.origin) {
      return DEFAULT_POST_LOGIN_ROUTE;
    }
    if (parsed.pathname === '/portal' || parsed.pathname === '/auth/callback') {
      return DEFAULT_POST_LOGIN_ROUTE;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return DEFAULT_POST_LOGIN_ROUTE;
  }
}
