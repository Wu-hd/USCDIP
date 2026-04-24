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

export function getWorkOrderDetail(workOrderId: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}`);
}

export function acceptWorkOrder(workOrderId: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/accept`, {
    method: 'POST',
    body: JSON.stringify({})
  });
}

export function completeWorkOrder(workOrderId: string, completionSummary: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ completionSummary })
  });
}

export function closeWorkOrder(workOrderId: string, closeReason: string, writebackType?: string, writebackReason?: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/close`, {
    method: 'POST',
    body: JSON.stringify({ closeReason, writebackType, writebackReason })
  });
}

export function writebackWorkOrder(workOrderId: string, writebackType: string, writebackReason: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/writeback`, {
    method: 'POST',
    body: JSON.stringify({ writebackType, writebackReason })
  });
}

export function dispatchWorkOrder(workOrderId: string, assigneeUserId: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/dispatch`, {
    method: 'POST',
    body: JSON.stringify({ assigneeUserId })
  });
}

export function transferWorkOrder(workOrderId: string, assigneeUserId: string) {
  return apiRequest<WorkOrderResponse>(`/api/workorders/${workOrderId}/transfer`, {
    method: 'POST',
    body: JSON.stringify({ assigneeUserId })
  });
}
