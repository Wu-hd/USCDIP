import { apiRequest } from '@/services/api';
import type { IncidentResponse, PageResponse } from '@/types/api';

export interface IncidentQuery {
  deviceId?: string;
  ruleCode?: string;
  status?: string;
  incidentType?: string;
}

export function getIncidents(page = 1, pageSize = 20, query: IncidentQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });

  return apiRequest<PageResponse<IncidentResponse>>(`/api/incidents?${params.toString()}`);
}
