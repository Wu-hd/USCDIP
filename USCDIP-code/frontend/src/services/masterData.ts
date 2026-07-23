import { apiRequest } from '@/services/api';
import type { GisObjectType, MasterDataRecordResponse, PageResponse, StationPipelineLayoutResponse } from '@/types/api';

const MASTER_DATA_ENDPOINTS: Record<GisObjectType, string> = {
  NODE: '/api/master/nodes',
  SEGMENT: '/api/master/segments',
  FACILITY: '/api/master/facilities',
  DEVICE: '/api/master/devices',
  STATION: '/api/master/stations'
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

export function getMonitoringStations(params: {
  provinceAdcode?: string;
  cityAdcode?: string;
  status?: string;
  page?: number;
  pageSize?: number;
} = {}) {
  const query = new URLSearchParams({
    page: String(params.page ?? 1),
    pageSize: String(params.pageSize ?? 50)
  });
  if (params.provinceAdcode) query.set('provinceAdcode', params.provinceAdcode);
  if (params.cityAdcode) query.set('cityAdcode', params.cityAdcode);
  if (params.status) query.set('status', params.status);
  return apiRequest<PageResponse<MasterDataRecordResponse>>(`/api/master/stations?${query.toString()}`);
}

export function getStationPipelineLayout(stationId: string) {
  return apiRequest<StationPipelineLayoutResponse>(
    `/api/master/stations/${encodeURIComponent(stationId)}/pipeline-layout`
  );
}

export function getMasterDataDetail(objectType: GisObjectType, objectId: string) {
  return apiRequest<MasterDataRecordResponse>(
    `${MASTER_DATA_ENDPOINTS[objectType]}/${encodeURIComponent(objectId)}`
  );
}
