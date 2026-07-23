import { apiRequest } from '@/services/api';
import type { MasterChangeResponse, MasterChangeSubmitRequest, PageResponse } from '@/types/api';

export interface MasterChangeListFilters {
  objectType?: string;
  objectId?: string;
  requestStatus?: string;
}

export function listMasterDataChanges(
  page = 1,
  pageSize = 20,
  filters: MasterChangeListFilters = {}
) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  if (filters.objectType) params.set('objectType', filters.objectType);
  if (filters.objectId) params.set('objectId', filters.objectId);
  if (filters.requestStatus) params.set('requestStatus', filters.requestStatus);

  return apiRequest<PageResponse<MasterChangeResponse>>(`/api/master/changes?${params.toString()}`);
}

export function submitMasterDataChange(request: MasterChangeSubmitRequest) {
  return apiRequest<MasterChangeResponse>('/api/master/changes', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}
