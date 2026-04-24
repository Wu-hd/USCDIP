import { apiRequest } from '@/services/api';
import type {
  AuthCallbackRequest,
  AuthLoginDescriptor,
  PendingOidcState,
  TokenPairResponse
} from '@/types/api';

const PENDING_OIDC_STATE_KEY = 'uscdip.oidc.pendingState';
const PROCESSED_OIDC_STATE_PREFIX = 'uscdip.oidc.processed.';

export function exchangeOidcCallback(request: AuthCallbackRequest) {
  return apiRequest<TokenPairResponse>('/api/auth/callback', {
    method: 'POST',
    body: JSON.stringify(request),
    skipAuthRefresh: true
  });
}

export function savePendingOidcState(
  descriptor: AuthLoginDescriptor,
  redirectUri: string
): void {
  if (!descriptor.state) {
    sessionStorage.removeItem(PENDING_OIDC_STATE_KEY);
    return;
  }

  const pendingState: PendingOidcState = {
    state: descriptor.state,
    redirectUri,
    stateExpiresAt: descriptor.stateExpiresAt
  };
  sessionStorage.setItem(PENDING_OIDC_STATE_KEY, JSON.stringify(pendingState));
}

export function getPendingOidcState(): PendingOidcState | null {
  const raw = sessionStorage.getItem(PENDING_OIDC_STATE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as PendingOidcState;
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
