import { apiRequest } from '@/services/api';
import type { PageResponse, WorkOrderResponse } from '@/types/api';

export interface WorkOrderQuery {
  status?: string;
  incidentId?: string;
  assignee?: string;
  workOrderType?: string;
}

export function getWorkOrders(page = 1, pageSize = 20, query: WorkOrderQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });

  return apiRequest<PageResponse<WorkOrderResponse>>(`/api/workorders?${params.toString()}`);
}
