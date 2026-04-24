import { apiRequest } from '@/services/api';
import type {
  AuthLoginDescriptor,
  AuthMePayload,
  BoundarySpec,
  NotificationResponse,
  PageResponse,
  PlatformBoundary,
  WorkOrderResponse
} from '@/types/api';

export function getPlatforms() {
  return apiRequest<PlatformBoundary[]>('/api/platforms');
}

export function getMenuBoundaries() {
  return apiRequest<BoundarySpec>('/api/menu-boundaries');
}

export function getLoginDescriptor(redirectUri: string) {
  const params = new URLSearchParams({ redirectUri });
  return apiRequest<AuthLoginDescriptor>(`/api/auth/login-url?${params.toString()}`, {
    skipAuthRefresh: true
  });
}

export function getCurrentUser() {
  return apiRequest<AuthMePayload>('/api/auth/me');
}

export function logout() {
  return apiRequest<Record<string, unknown>>('/api/auth/logout', {
    method: 'POST',
    skipAuthRefresh: true,
    retryOnUnauthorized: false
  });
}

export function getWorkOrders() {
  return apiRequest<PageResponse<WorkOrderResponse>>('/api/workorders?page=1&pageSize=5');
}

export function getNotifications() {
  return apiRequest<PageResponse<NotificationResponse>>('/api/notifications?page=1&pageSize=5');
}
