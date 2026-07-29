import { apiRequest } from '@/services/api';
import type {
  AuthCallbackRequest,
  AuthLoginDescriptor,
  PendingOidcState,
  TokenPairResponse
} from '@/types/api';

const PENDING_OIDC_STATE_KEY = 'uscdip.oidc.pendingState';
const PROCESSED_OIDC_STATE_PREFIX = 'uscdip.oidc.processed.';
const POST_LOGIN_LAUNCH_STARTED_AT_KEY = 'uscdip.launch.startedAt';
export const DEFAULT_POST_LOGIN_ROUTE = '/home';
export const POST_LOGIN_LAUNCH_ROUTE = '/launch';

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

export function buildPostLoginLaunchRoute(target: unknown): string {
  const redirect = resolvePostLoginRoute(target);
  sessionStorage.setItem(POST_LOGIN_LAUNCH_STARTED_AT_KEY, String(Date.now()));
  return `${POST_LOGIN_LAUNCH_ROUTE}?redirect=${encodeURIComponent(redirect)}`;
}

export function consumePostLoginLaunchElapsedMs(): number {
  const raw = sessionStorage.getItem(POST_LOGIN_LAUNCH_STARTED_AT_KEY);
  sessionStorage.removeItem(POST_LOGIN_LAUNCH_STARTED_AT_KEY);
  const startedAt = Number(raw);
  if (!Number.isFinite(startedAt) || startedAt <= 0) return 0;
  const elapsed = Date.now() - startedAt;
  return elapsed > 0 && elapsed < 60_000 ? elapsed : 0;
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
    if (
      parsed.pathname === '/portal'
      || parsed.pathname === '/auth/callback'
      || parsed.pathname === POST_LOGIN_LAUNCH_ROUTE
    ) {
      return DEFAULT_POST_LOGIN_ROUTE;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return DEFAULT_POST_LOGIN_ROUTE;
  }
}
