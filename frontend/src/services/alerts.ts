import { apiRequest } from '@/services/api';
import type { AlertRecordResponse, PageResponse } from '@/types/api';

export interface AlertQuery {
  deviceId?: string;
  ruleCode?: string;
  decision?: string;
  severity?: string;
  sourceBatchId?: string;
}

export function getAlerts(page = 1, pageSize = 50, query: AlertQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });
  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });
  return apiRequest<PageResponse<AlertRecordResponse>>(`/api/alerts?${params.toString()}`);
}
