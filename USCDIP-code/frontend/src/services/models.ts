import { apiRequest } from '@/services/api';
import type { ModelResponse, ModelRollbackRequest, ModelVersionResponse, PageResponse } from '@/types/api';

export interface ModelQuery {
  status?: string;
}

export function getModels(page = 1, pageSize = 20, query: ModelQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });

  return apiRequest<PageResponse<ModelResponse>>(`/api/models?${params.toString()}`);
}

export function getModelDetail(modelCode: string) {
  return apiRequest<ModelResponse>(`/api/models/${encodeURIComponent(modelCode)}`);
}

export function rollbackModel(modelCode: string, request: ModelRollbackRequest) {
  return apiRequest<ModelVersionResponse>(`/api/models/${encodeURIComponent(modelCode)}/rollback`, {
    method: 'POST',
    body: JSON.stringify(request)
  });
}
