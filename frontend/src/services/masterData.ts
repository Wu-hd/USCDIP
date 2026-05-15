import { apiRequest } from '@/services/api';
import type { GisObjectType, MasterDataRecordResponse, PageResponse } from '@/types/api';

const MASTER_DATA_ENDPOINTS: Record<GisObjectType, string> = {
  NODE: '/api/master/nodes',
  SEGMENT: '/api/master/segments',
  FACILITY: '/api/master/facilities',
  DEVICE: '/api/master/devices'
};

export function getMasterDataPage(objectType: GisObjectType, page = 1, pageSize = 100) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  return apiRequest<PageResponse<MasterDataRecordResponse>>(
    `${MASTER_DATA_ENDPOINTS[objectType]}?${params.toString()}`
  );
}

export function getMasterDataDetail(objectType: GisObjectType, objectId: string) {
  return apiRequest<MasterDataRecordResponse>(
    `${MASTER_DATA_ENDPOINTS[objectType]}/${encodeURIComponent(objectId)}`
  );
}
