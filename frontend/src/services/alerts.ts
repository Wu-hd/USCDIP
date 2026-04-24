import { apiRequest } from '@/services/api';
import type { AlertRecordResponse, PageResponse } from '@/types/api';

export function getAlerts(page = 1, pageSize = 50) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });
  return apiRequest<PageResponse<AlertRecordResponse>>(`/api/alerts?${params.toString()}`);
}
