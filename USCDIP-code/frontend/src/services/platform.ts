import { apiRequest } from '@/services/api';
import type { PlatformBoundary } from '@/types/api';

export function getPlatform(platformCode: string) {
  return apiRequest<PlatformBoundary>(`/api/platforms/${encodeURIComponent(platformCode)}`);
}
